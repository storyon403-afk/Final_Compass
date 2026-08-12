package cn.finalscompass.service;

import cn.finalscompass.ai.runtime.provider.RuntimeProviderCatalog;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiAnalysisService {
  private static final String CONSENT_VERSION = "2026-08-v1";
  private final JdbcClient jdbc;
  private final ActivityService activity;
  private final AiSecretCipher cipher;
  private final RuntimeProviderCatalog providers;

  public AiAnalysisService(
      JdbcClient jdbc,
      ActivityService activity,
      AiSecretCipher cipher,
      RuntimeProviderCatalog providers) {
    this.jdbc = jdbc;
    this.activity = activity;
    this.cipher = cipher;
    this.providers = providers;
  }

  public Dashboard dashboard(long userId) {
    activity.ensureCurrentEntitlements();
    boolean admin =
        jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
            .param("user", userId)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    List<Map<String, Object>> providerConfigs =
        admin
            ? jdbc.sql(
                    """
                    SELECT provider,model_name,enabled,key_fingerprint,updated_at
                    FROM platform_ai_config ORDER BY provider
                    """)
                .query()
                .listOfRows()
            : List.of();
    List<Map<String, Object>> secrets =
        jdbc.sql(
                """
                SELECT provider,key_fingerprint,key_label,consent_version,consented_at,updated_at
                FROM user_ai_secret WHERE user_id=:user ORDER BY provider
                """)
            .param("user", userId)
            .query()
            .listOfRows();
    List<Map<String, Object>> reviewSecrets =
        jdbc.sql(
                """
                SELECT provider,key_fingerprint,key_label,consent_version,consented_at,updated_at
                FROM user_ai_review_secret WHERE user_id=:user ORDER BY provider
                """)
            .param("user", userId)
            .query()
            .listOfRows();
    String configuredDefault =
        jdbc.sql("SELECT default_provider FROM platform_ai_setting WHERE id=1")
            .query(String.class)
            .optional()
            .orElse(null);
    boolean platformDefaultAvailable =
        configuredDefault != null
            && jdbc.sql("SELECT enabled FROM platform_ai_config WHERE provider=:provider")
                .param("provider", configuredDefault)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    boolean hermesPlatformAvailable =
        jdbc.sql("SELECT enabled FROM platform_ai_config WHERE provider='hermes'")
            .query(Boolean.class)
            .optional()
            .orElse(false);
    Map<String, Object> reviewConfig =
        jdbc
            .sql(
                admin
                    ? """
SELECT provider,model_name,enabled,key_fingerprint,updated_at FROM platform_ai_review_config WHERE id=1
"""
                    : "SELECT enabled FROM platform_ai_review_config WHERE id=1")
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .orElse(Map.of());
    List<Map<String, Object>> skills =
        admin
            ? jdbc.sql(
                    """
                    SELECT skill_key AS id,skill_type AS category,name,description
                    FROM ai_runtime_skill WHERE status='ACTIVE' ORDER BY skill_key
                    """)
                .query()
                .listOfRows()
            : List.of();
    return new Dashboard(
        YearMonth.now().toString(),
        activity.currentMonthScore(userId),
        activity.hasPlatformEntitlement(userId),
        activity.currentMonthLeaderboard(),
        skills,
        providers.availableModelProviders(),
        providerConfigs,
        secrets,
        reviewSecrets,
        cipher.available(),
        admin ? configuredDefault : null,
        platformDefaultAvailable,
        hermesPlatformAvailable,
        reviewConfig);
  }

  @Transactional
  public Map<String, Object> saveUserKey(long userId, SaveUserKey request) {
    String provider = providers.require(request.provider());
    if (!request.consentToStore())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "保存 API Key 前必须获得用户明确同意");
    if (!cipher.available())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "当前环境未配置 API Key 加密主密钥，只能使用不保存模式");
    char[] apiKey = requiredApiKey(request.apiKey());
    try {
      var encrypted = cipher.encrypt(apiKey);
      jdbc.sql(
              """
INSERT INTO user_ai_secret(user_id,provider,encrypted_key,encryption_iv,key_fingerprint,key_label,consent_version)
VALUES (:user,:provider,:encrypted,:iv,:fingerprint,:label,:consent)
ON DUPLICATE KEY UPDATE encrypted_key=:encrypted,encryption_iv=:iv,key_fingerprint=:fingerprint,
  key_label=:label,consent_version=:consent,consented_at=NOW(),updated_at=NOW()
""")
          .param("user", userId)
          .param("provider", provider)
          .param("encrypted", encrypted.ciphertext())
          .param("iv", encrypted.iv())
          .param("fingerprint", encrypted.fingerprint())
          .param("label", cleanLabel(request.label()))
          .param("consent", CONSENT_VERSION)
          .update();
      return Map.of("provider", provider, "fingerprint", encrypted.fingerprint(), "saved", true);
    } finally {
      Arrays.fill(apiKey, '\0');
    }
  }

  public void deleteUserKey(long userId, String provider) {
    jdbc.sql("DELETE FROM user_ai_secret WHERE user_id=:user AND provider=:provider")
        .param("user", userId)
        .param("provider", providers.require(provider))
        .update();
  }

  @Transactional
  public Map<String, Object> saveUserReviewKey(long userId, SaveUserKey request) {
    String provider = providers.require(request.provider());
    if (!request.consentToStore())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "保存 API Key 前必须获得用户明确同意");
    if (!cipher.available())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "当前环境未配置 API Key 加密主密钥，只能使用不保存模式");
    char[] apiKey = requiredApiKey(request.apiKey());
    try {
      var encrypted = cipher.encrypt(apiKey);
      jdbc.sql(
              """
INSERT INTO user_ai_review_secret(user_id,provider,encrypted_key,encryption_iv,key_fingerprint,key_label,consent_version)
VALUES(:user,:provider,:encrypted,:iv,:fingerprint,:label,:consent)
ON DUPLICATE KEY UPDATE encrypted_key=:encrypted,encryption_iv=:iv,key_fingerprint=:fingerprint,
  key_label=:label,consent_version=:consent,consented_at=NOW(),updated_at=NOW()
""")
          .param("user", userId)
          .param("provider", provider)
          .param("encrypted", encrypted.ciphertext())
          .param("iv", encrypted.iv())
          .param("fingerprint", encrypted.fingerprint())
          .param("label", cleanLabel(request.label()))
          .param("consent", CONSENT_VERSION)
          .update();
      return Map.of("provider", provider, "fingerprint", encrypted.fingerprint(), "saved", true);
    } finally {
      Arrays.fill(apiKey, '\0');
    }
  }

  @Transactional
  public Map<String, Object> savePlatformKey(long adminId, SavePlatformKey request) {
    String provider = providers.require(request.provider());
    if (!cipher.available())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境未配置 API Key 加密主密钥");
    char[] apiKey = requiredApiKey(request.apiKey());
    try {
      var encrypted = cipher.encrypt(apiKey);
      jdbc.sql(
              """
INSERT INTO platform_ai_config(provider,encrypted_key,encryption_iv,key_fingerprint,model_name,enabled,updated_by)
VALUES (:provider,:encrypted,:iv,:fingerprint,:model,:enabled,:admin)
ON DUPLICATE KEY UPDATE encrypted_key=:encrypted,encryption_iv=:iv,key_fingerprint=:fingerprint,
  model_name=:model,enabled=:enabled,updated_by=:admin,updated_at=NOW()
""")
          .param("provider", provider)
          .param("encrypted", encrypted.ciphertext())
          .param("iv", encrypted.iv())
          .param("fingerprint", encrypted.fingerprint())
          .param("model", cleanModel(request.model()))
          .param("enabled", request.enabled())
          .param("admin", adminId)
          .update();
      return Map.of(
          "provider",
          provider,
          "fingerprint",
          encrypted.fingerprint(),
          "enabled",
          request.enabled());
    } finally {
      Arrays.fill(apiKey, '\0');
    }
  }

  @Transactional
  public Map<String, Object> savePlatformDefault(long adminId, PlatformDefaultRequest request) {
    String provider = providers.require(request.provider());
    if (providers.available().stream()
        .anyMatch(item -> item.id().equals(provider) && item.capabilities().contains("AGENT")))
      throw new IllegalArgumentException("外部 Agent 不能作为本地模型 Provider");
    boolean enabled =
        jdbc.sql("SELECT enabled FROM platform_ai_config WHERE provider=:provider")
            .param("provider", provider)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    if (!enabled) throw new IllegalArgumentException("请先配置并启用该 Provider");
    jdbc.sql(
            "UPDATE platform_ai_setting SET default_provider=:provider,updated_by=:admin WHERE"
                + " id=1")
        .param("provider", provider)
        .param("admin", adminId)
        .update();
    return Map.of("defaultProvider", provider);
  }

  @Transactional
  public Map<String, Object> savePlatformReviewKey(long adminId, SavePlatformKey request) {
    String provider = providers.require(request.provider());
    if (!cipher.available())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境未配置 API Key 加密主密钥");
    char[] apiKey = requiredApiKey(request.apiKey());
    try {
      var encrypted = cipher.encrypt(apiKey);
      jdbc.sql(
              """
INSERT INTO platform_ai_review_config(id,provider,model_name,encrypted_key,encryption_iv,key_fingerprint,enabled,updated_by)
VALUES (1,:provider,:model,:encrypted,:iv,:fingerprint,:enabled,:admin)
ON DUPLICATE KEY UPDATE provider=:provider,model_name=:model,encrypted_key=:encrypted,
  encryption_iv=:iv,key_fingerprint=:fingerprint,enabled=:enabled,updated_by=:admin,updated_at=NOW()
""")
          .param("provider", provider)
          .param("model", cleanModel(request.model()))
          .param("encrypted", encrypted.ciphertext())
          .param("iv", encrypted.iv())
          .param("fingerprint", encrypted.fingerprint())
          .param("enabled", request.enabled())
          .param("admin", adminId)
          .update();
      return Map.of(
          "provider",
          provider,
          "fingerprint",
          encrypted.fingerprint(),
          "enabled",
          request.enabled());
    } finally {
      Arrays.fill(apiKey, '\0');
    }
  }

  private char[] requiredApiKey(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("API Key 不能为空");
    return value.toCharArray();
  }

  private String cleanLabel(String value) {
    return value == null || value.isBlank()
        ? null
        : value.trim().substring(0, Math.min(80, value.trim().length()));
  }

  private String cleanModel(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("模型名称不能为空");
    return value.trim().substring(0, Math.min(120, value.trim().length()));
  }

  public record Dashboard(
      String month,
      int myScore,
      boolean platformEligible,
      List<Map<String, Object>> leaderboard,
      List<Map<String, Object>> skills,
      List<RuntimeProviderCatalog.ProviderInfo> providers,
      List<Map<String, Object>> platformProviders,
      List<Map<String, Object>> savedKeys,
      List<Map<String, Object>> reviewSavedKeys,
      boolean encryptedStorageAvailable,
      String defaultProvider,
      boolean platformDefaultAvailable,
      boolean hermesPlatformAvailable,
      Map<String, Object> platformReviewConfig) {}

  public record SaveUserKey(String provider, String apiKey, String label, boolean consentToStore) {}

  public record SavePlatformKey(String provider, String model, String apiKey, boolean enabled) {}

  public record PlatformDefaultRequest(String provider) {}
}
