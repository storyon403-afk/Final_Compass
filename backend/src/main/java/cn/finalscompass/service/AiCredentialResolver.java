package cn.finalscompass.service;

import cn.finalscompass.ai.AiCredentialSource;
import cn.finalscompass.ai.AiProviderGateway;
import cn.finalscompass.ai.ResolvedAiCredential;
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

    public ResolvedAiCredential resolve(long userId, String providerValue, AiCredentialSource source, String ephemeral) {
        String provider = providers.require(providerValue).id();
        if (source == AiCredentialSource.PLATFORM) {
            boolean admin = jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
                    .param("user", userId).query(Boolean.class).optional().orElse(false);
            if (!admin && !activity.hasPlatformEntitlement(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本月暂无平台 AI 免费资格，可使用自己的 API Key");
            }
            SecretRow row = jdbc.sql("""
                SELECT provider,model_name,encrypted_key,encryption_iv FROM platform_ai_config
                WHERE provider=:provider AND enabled=TRUE
                """).param("provider", provider).query(SecretRow.class).optional()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未启用该平台 AI"));
            return new ResolvedAiCredential(provider, row.modelName(), source,
                    cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
        }
        if (source == AiCredentialSource.STORED_BYOK) {
            UserSecretRow row = jdbc.sql("""
                SELECT provider,encrypted_key,encryption_iv FROM user_ai_secret
                WHERE user_id=:user AND provider=:provider
                """).param("user", userId).param("provider", provider).query(UserSecretRow.class).optional()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "尚未保存该 Provider 的 API Key"));
            return new ResolvedAiCredential(provider, "user-selected", source,
                    cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
        }
        if (ephemeral == null || ephemeral.length() < 8 || ephemeral.length() > 500) {
            throw new IllegalArgumentException("请输入本次请求使用的 API Key");
        }
        return new ResolvedAiCredential(provider, "user-selected", source, ephemeral.toCharArray());
    }

    private record SecretRow(String provider, String modelName, String encryptedKey, String encryptionIv) {}
    private record UserSecretRow(String provider, String encryptedKey, String encryptionIv) {}
}
