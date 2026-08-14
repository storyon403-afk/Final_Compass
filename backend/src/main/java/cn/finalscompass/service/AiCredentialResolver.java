package cn.finalscompass.service;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderCatalog;
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
  private final RuntimeProviderCatalog providers;

  public AiCredentialResolver(
      JdbcClient jdbc,
      ActivityService activity,
      AiSecretCipher cipher,
      RuntimeProviderCatalog providers) {
    this.jdbc = jdbc;
    this.activity = activity;
    this.cipher = cipher;
    this.providers = providers;
  }

  public ResolvedAiCredential resolve(
      long userId,
      String runtime,
      String providerValue,
      String modelValue,
      AiCredentialSource source,
      String ephemeral) {
    if (source == AiCredentialSource.PLATFORM) {
      boolean admin =
          jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
              .param("user", userId)
              .query(Boolean.class)
              .optional()
              .orElse(false);
      boolean internalTestOpen =
          jdbc.sql("SELECT internal_test_open FROM platform_ai_setting WHERE id=1")
              .query(Boolean.class)
              .optional()
              .orElse(false);
      if (!admin && !internalTestOpen && !activity.hasPlatformEntitlement(userId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本月暂无平台 AI 免费资格，可使用自己的 API Key");
      }
      SecretRow row =
          providerValue != null && !providerValue.isBlank()
              ? platformProvider(providers.require(providerValue))
              : jdbc.sql(
                      """
SELECT c.provider,c.model_name,c.encrypted_key,c.encryption_iv
FROM platform_ai_setting s JOIN platform_ai_config c ON c.provider=s.default_provider
WHERE s.id=1 AND c.enabled=TRUE
""")
                  .query(SecretRow.class)
                  .optional()
                  .orElseThrow(
                      () ->
                          new ResponseStatusException(
                              HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未配置平台默认 AI 模型"));
      String selectedModel =
          modelValue == null || modelValue.isBlank()
              ? row.modelName()
              : validateModel(row.provider(), modelValue);
      return new ResolvedAiCredential(
          row.provider(),
          selectedModel,
          source,
          cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
    }
    String provider = providers.require(providerValue);
    String model = validateModel(provider, modelValue);
    if (source == AiCredentialSource.STORED_BYOK) {
      UserSecretRow row =
          jdbc.sql(
                  """
                  SELECT provider,encrypted_key,encryption_iv FROM user_ai_secret
                  WHERE user_id=:user AND provider=:provider
                  """)
              .param("user", userId)
              .param("provider", provider)
              .query(UserSecretRow.class)
              .optional()
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "尚未保存该 Provider 的 API Key"));
      return new ResolvedAiCredential(
          provider, model, source, cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
    }
    if (ephemeral == null || ephemeral.length() < 8 || ephemeral.length() > 500) {
      throw new IllegalArgumentException("请输入本次请求使用的 API Key");
    }
    return new ResolvedAiCredential(provider, model, source, ephemeral.toCharArray());
  }

  private SecretRow platformProvider(String provider) {
    return jdbc.sql(
            """
            SELECT provider,model_name,encrypted_key,encryption_iv FROM platform_ai_config
            WHERE provider=:provider AND enabled=TRUE
            """)
        .param("provider", provider)
        .query(SecretRow.class)
        .optional()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未启用该 Provider 通道"));
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
    String provider = providers.require(providerValue);
    boolean admin =
        jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
            .param("user", userId)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    if (!admin && !activity.hasPlatformEntitlement(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本月暂无平台视觉识别资格");
    }
    SecretRow row =
        jdbc.sql(
                """
                SELECT provider,model_name,encrypted_key,encryption_iv FROM platform_ai_config
                WHERE provider=:provider AND enabled=TRUE
                """)
            .param("provider", provider)
            .query(SecretRow.class)
            .optional()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未配置 Gemini 视觉识别通道"));
    return new ResolvedAiCredential(
        provider,
        row.modelName(),
        AiCredentialSource.PLATFORM,
        cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
  }

  /**
   * Platform-owned infrastructure credential; callers must already be trusted service components.
   */
  public ResolvedAiCredential resolvePlatformService(String providerValue, String modelValue) {
    String provider = providers.require(providerValue);
    SecretRow row = platformProvider(provider);
    return new ResolvedAiCredential(
        provider,
        validateModel(provider, modelValue),
        AiCredentialSource.PLATFORM,
        cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
  }

  public ResolvedAiCredential resolvePlatformReview(long userId) {
    boolean admin =
        jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
            .param("user", userId)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    if (!admin && !activity.hasPlatformEntitlement(userId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "本月暂无平台 AI 审核额度");
    SecretRow row =
        jdbc.sql(
                """
                SELECT provider,model_name,encrypted_key,encryption_iv
                FROM platform_ai_review_config WHERE id=1 AND enabled=TRUE
                """)
            .query(SecretRow.class)
            .optional()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "管理员尚未配置 MultiWeb AI 平台审核模型"));
    return new ResolvedAiCredential(
        row.provider(),
        row.modelName(),
        AiCredentialSource.PLATFORM,
        cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
  }

  public ResolvedAiCredential resolveUserReview(
      long userId,
      String providerValue,
      String modelValue,
      AiCredentialSource source,
      String ephemeral) {
    if (source == AiCredentialSource.PLATFORM) return resolvePlatformReview(userId);
    String provider = providers.require(providerValue), model = validateModel(provider, modelValue);
    if (source == AiCredentialSource.STORED_BYOK) {
      UserSecretRow row =
          jdbc.sql(
                  """
                  SELECT provider,encrypted_key,encryption_iv FROM user_ai_review_secret
                  WHERE user_id=:user AND provider=:provider
                  """)
              .param("user", userId)
              .param("provider", provider)
              .query(UserSecretRow.class)
              .optional()
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.BAD_REQUEST, "尚未保存该 Provider 的 MultiWeb AI 审核 Key"));
      return new ResolvedAiCredential(
          provider, model, source, cipher.decrypt(row.encryptedKey(), row.encryptionIv()));
    }
    if (ephemeral == null || ephemeral.length() < 8 || ephemeral.length() > 500)
      throw new IllegalArgumentException("请输入本次审核使用的 API Key");
    return new ResolvedAiCredential(provider, model, source, ephemeral.toCharArray());
  }

  /** 解析用户独立视觉链路凭据；平台凭据继续走现有平台视觉通道。 */
  public ResolvedAiCredential resolveUserVision(long userId,String providerValue,String modelValue,AiCredentialSource source,String ephemeral){
    String provider=providers.require(providerValue),model=validateModel(provider,modelValue);
    if(source==AiCredentialSource.PLATFORM)return resolvePlatformAuxiliary(userId,provider);
    if(source==AiCredentialSource.STORED_BYOK){
      UserSecretRow row=jdbc.sql("SELECT provider,encrypted_key,encryption_iv FROM user_ai_vision_secret WHERE user_id=:user AND provider=:provider").param("user",userId).param("provider",provider).query(UserSecretRow.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"尚未保存该视觉 Provider 的 API Key"));
      return new ResolvedAiCredential(provider,model,source,cipher.decrypt(row.encryptedKey(),row.encryptionIv()));
    }
    if(ephemeral==null||ephemeral.length()<8||ephemeral.length()>500)throw new IllegalArgumentException("请输入本次视觉请求使用的 API Key");
    return new ResolvedAiCredential(provider,model,source,ephemeral.toCharArray());
  }

  private record SecretRow(
      String provider, String modelName, String encryptedKey, String encryptionIv) {}

  private record UserSecretRow(String provider, String encryptedKey, String encryptionIv) {}
}
