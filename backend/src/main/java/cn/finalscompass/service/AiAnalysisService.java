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
    List<Map<String,Object>> visionSecrets=jdbc.sql("SELECT provider,key_fingerprint,key_label,consent_version,consented_at,updated_at FROM user_ai_vision_secret WHERE user_id=:user ORDER BY provider").param("user",userId).query().listOfRows();
    Map<String,Object> visionFeatures=jdbc.sql("SELECT user_vision_auxiliary_enabled,user_vision_ephemeral_key_enabled,user_vision_stored_key_enabled,default_vision_provider FROM ai_feature_setting WHERE id=1").query().singleRow();
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
        visionSecrets,
        visionFeatures,
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

  /** 保存用户专用视觉 Key，与主模型和审核模型凭据隔离。 */
  @Transactional public Map<String,Object> saveUserVisionKey(long userId,SaveUserKey request){
    String provider=providers.require(request.provider());
    if(!List.of("gemini","doubao").contains(provider))throw new IllegalArgumentException("视觉 Provider 仅支持 Gemini 或 Doubao");
    boolean enabled=jdbc.sql("SELECT user_vision_stored_key_enabled FROM ai_feature_setting WHERE id=1").query(Boolean.class).single();
    if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"管理员已关闭视觉 Key 保存功能");
    if(!request.consentToStore())throw new IllegalArgumentException("保存 API Key 前必须获得用户明确同意");
    char[] apiKey=requiredApiKey(request.apiKey());try{var encrypted=cipher.encrypt(apiKey);jdbc.sql("INSERT INTO user_ai_vision_secret(user_id,provider,encrypted_key,encryption_iv,key_fingerprint,key_label,consent_version) VALUES(:user,:provider,:encrypted,:iv,:fingerprint,:label,:consent) ON DUPLICATE KEY UPDATE encrypted_key=:encrypted,encryption_iv=:iv,key_fingerprint=:fingerprint,key_label=:label,consent_version=:consent,consented_at=NOW(),updated_at=NOW()")
      .param("user",userId).param("provider",provider).param("encrypted",encrypted.ciphertext()).param("iv",encrypted.iv()).param("fingerprint",encrypted.fingerprint()).param("label",cleanLabel(request.label())).param("consent",CONSENT_VERSION).update();return Map.of("provider",provider,"fingerprint",encrypted.fingerprint(),"saved",true);}finally{Arrays.fill(apiKey,'\0');}
  }
  public void deleteUserVisionKey(long userId,String provider){jdbc.sql("DELETE FROM user_ai_vision_secret WHERE user_id=:user AND provider=:provider").param("user",userId).param("provider",providers.require(provider)).update();}

  public Map<String,Object> updateVisionFeatures(long adminId,VisionFeatureUpdate input){
    String provider=providers.require(input.defaultVisionProvider());if(!List.of("gemini","doubao").contains(provider))throw new IllegalArgumentException("默认视觉 Provider 不合法");
    jdbc.sql("UPDATE ai_feature_setting SET user_vision_auxiliary_enabled=:aux,user_vision_ephemeral_key_enabled=:ephemeral,user_vision_stored_key_enabled=:stored,default_vision_provider=:provider,updated_by=:admin WHERE id=1").param("aux",input.auxiliaryEnabled()).param("ephemeral",input.ephemeralEnabled()).param("stored",input.storedEnabled()).param("provider",provider).param("admin",adminId).update();return Map.of("updated",true);
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
    String model = cleanModel(request.model());
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
          .param("model", model)
          .param("enabled", request.enabled())
          .param("admin", adminId)
          .update();
      // 管理员配置的新模型必须同步进入 Runtime 注册表，否则旧配置表显示保存成功，路由器却找不到它。
      if (!"hermes".equals(provider)) registerPlatformModel(provider, model, request.enabled());
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
    String model = value.trim();
    if (!model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{1,119}"))
      throw new IllegalArgumentException("模型名称格式不合法");
    return model;
  }

  /**
   * 将管理员平台配置同步到统一 Runtime 模型目录。
   * 新模型默认只声明文本推理能力；视觉、工具和结构化输出必须在确认协议实现后单独登记。
   */
  private void registerPlatformModel(String provider, String model, boolean enabled) {
    jdbc.sql(
            """
INSERT INTO ai_runtime_provider_model(
  provider_id,model_key,display_name,status,routing_priority,routing_weight,configuration
)
SELECT id,:model,:model,:status,100,100,JSON_OBJECT('source','platform-admin')
FROM ai_runtime_provider WHERE provider_key=:provider
ON DUPLICATE KEY UPDATE display_name=:model,status=:status,updated_at=NOW()
""")
        .param("provider", provider)
        .param("model", model)
        .param("status", enabled ? "ACTIVE" : "DISABLED")
        .update();
    if (!enabled) return;
    jdbc.sql(
            """
INSERT IGNORE INTO ai_runtime_provider_model_capability(
  provider_model_id,capability_id,configuration,verified_at
)
SELECT model.id,capability.id,JSON_OBJECT('source','platform-admin'),CURRENT_TIMESTAMP
FROM ai_runtime_provider_model model
JOIN ai_runtime_provider provider ON provider.id=model.provider_id
JOIN ai_runtime_capability capability ON capability.capability_key='TEXT_REASONING'
WHERE provider.provider_key=:provider AND model.model_key=:model
""")
        .param("provider", provider)
        .param("model", model)
        .update();
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
      List<Map<String,Object>> visionSavedKeys,
      Map<String,Object> visionFeatures,
      boolean encryptedStorageAvailable,
      String defaultProvider,
      boolean platformDefaultAvailable,
      boolean hermesPlatformAvailable,
      Map<String, Object> platformReviewConfig) {}

  public record SaveUserKey(String provider, String apiKey, String label, boolean consentToStore) {}

  public record SavePlatformKey(String provider, String model, String apiKey, boolean enabled) {}

  public record PlatformDefaultRequest(String provider) {}
  public record VisionFeatureUpdate(boolean auxiliaryEnabled,boolean ephemeralEnabled,boolean storedEnabled,String defaultVisionProvider){}
}
