package cn.finalscompass.service;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.provider.AiProviderGateway;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves platform, stored-BYOK, and ephemeral-BYOK credentials behind one boundary. */
@Component
public class AiCredentialResolver {
    private final JdbcClient jdbc;
    private final ActivityService activity;
    private final AiSecretCipher cipher;
    private final AiProviderGateway providers;

    public AiCredentialResolver(JdbcClient jdbc, ActivityService activity, AiSecretCipher cipher,
                                AiProviderGateway providers) {
        this.jdbc = jdbc;
        this.activity = activity;
        this.cipher = cipher;
        this.providers = providers;
    }

    public ResolvedAiCredential resolve(long userId, String runtime, String providerValue, String modelValue,
                                        AiCredentialSource source, String ephemeral) {
        if (source == AiCredentialSource.PLATFORM) {
            boolean admin = jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
                    .param("user", userId).query(Boolean.class).optional().orElse(false);
            if (!admin && !activity.hasPlatformEntitlement(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本月暂无平台 AI 免费资格，可使用自己的 API Key");
            }
            SecretRow row = "HERMES".equalsIgnoreCase(runtime)
                    ? platformProvider("hermes")
                    : jdbc.sql("""
                        SELECT c.provider,c.model_name,c.encrypted_key,c.encryption_iv
                        FROM platform_ai_setting s JOIN platform_ai_config c ON c.provider=s.default_provider
                        WHERE s.id=1 AND c.enabled=TRUE
                        """).query(SecretRow.class).optional().orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未配置平台默认 AI 模型"));
            return new ResolvedAiCredential(row.provider(), row.modelName(), source,
                    cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
        }
        String provider = "HERMES".equalsIgnoreCase(runtime) ? "hermes" : providers.require(providerValue).id();
        providers.require(provider);
        String model = validateModel(provider, modelValue);
        if (source == AiCredentialSource.STORED_BYOK) {
            UserSecretRow row = jdbc.sql("""
                SELECT provider,encrypted_key,encryption_iv FROM user_ai_secret
                WHERE user_id=:user AND provider=:provider
                """).param("user", userId).param("provider", provider).query(UserSecretRow.class).optional()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "尚未保存该 Provider 的 API Key"));
            return new ResolvedAiCredential(provider, model, source,
                    cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
        }
        if (ephemeral == null || ephemeral.length() < 8 || ephemeral.length() > 500) {
            throw new IllegalArgumentException("请输入本次请求使用的 API Key");
        }
        return new ResolvedAiCredential(provider, model, source, ephemeral.toCharArray());
    }

    private SecretRow platformProvider(String provider) {
        return jdbc.sql("""
                SELECT provider,model_name,encrypted_key,encryption_iv FROM platform_ai_config
                WHERE provider=:provider AND enabled=TRUE
                """).param("provider", provider).query(SecretRow.class).optional().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未启用该 Agent Runtime"));
    }

    private String validateModel(String provider, String value) {
        if ("hermes".equals(provider)) return "hermes-agent";
        String model = value == null ? "" : value.trim();
        if (!model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{1,119}"))
            throw new IllegalArgumentException("请选择有效的模型");
        return model;
    }

    /** Resolves a platform-owned auxiliary model used inside an authorized multi-stage invocation. */
    public ResolvedAiCredential resolvePlatformAuxiliary(long userId, String providerValue) {
        String provider = providers.require(providerValue).id();
        boolean admin = jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
                .param("user", userId).query(Boolean.class).optional().orElse(false);
        if (!admin && !activity.hasPlatformEntitlement(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本月暂无平台视觉识别资格");
        }
        SecretRow row = jdbc.sql("""
            SELECT provider,model_name,encrypted_key,encryption_iv FROM platform_ai_config
            WHERE provider=:provider AND enabled=TRUE
            """).param("provider", provider).query(SecretRow.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "管理员尚未配置 Gemini 视觉识别通道"));
        return new ResolvedAiCredential(provider, row.modelName(), AiCredentialSource.PLATFORM,
                cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
    }

    private record SecretRow(String provider, String modelName, String encryptedKey, String encryptionIv) {}
    private record UserSecretRow(String provider, String encryptedKey, String encryptionIv) {}
}
