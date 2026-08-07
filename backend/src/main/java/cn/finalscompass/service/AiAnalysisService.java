package cn.finalscompass.service;

import cn.finalscompass.ai.agent.AiAgentOrchestrator;
import cn.finalscompass.ai.agent.AiSkillPlanner;
import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.provider.AiProviderAdapter;
import cn.finalscompass.ai.provider.AiProviderGateway;
import cn.finalscompass.ai.skill.AiSkill;
import cn.finalscompass.ai.skill.AiSkillRegistry;
import cn.finalscompass.ai.task.AiTaskRepository;
import cn.finalscompass.ai.task.AiTaskStatus;
import cn.finalscompass.ai.context.CourseContextLoader;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiAnalysisService {
    private static final String CONSENT_VERSION = "2026-08-v1";
    private final JdbcClient jdbc;
    private final ActivityService activity;
    private final AiSecretCipher cipher;
    private final AiSkillRegistry skills;
    private final AiAgentOrchestrator orchestrator;
    private final AiProviderGateway gateway;
    private final AiCredentialResolver credentials;
    private final TransientAiImageService images;
    private final AiUsageGuardService usageGuard;
    private final AiVisionProblemPipeline visionPipeline;
    private final AiTaskRepository tasks;
    private final CourseContextLoader courseContexts;

    public AiAnalysisService(JdbcClient jdbc, ActivityService activity, AiSecretCipher cipher,
                             AiSkillRegistry skills, AiAgentOrchestrator orchestrator,
                             AiProviderGateway gateway, AiCredentialResolver credentials,
                             TransientAiImageService images, AiUsageGuardService usageGuard,
                             AiVisionProblemPipeline visionPipeline, AiTaskRepository tasks,
                             CourseContextLoader courseContexts) {
        this.jdbc = jdbc;
        this.activity = activity;
        this.cipher = cipher;
        this.skills = skills;
        this.orchestrator = orchestrator;
        this.gateway = gateway;
        this.credentials = credentials;
        this.images = images;
        this.usageGuard = usageGuard;
        this.visionPipeline = visionPipeline;
        this.tasks = tasks;
        this.courseContexts = courseContexts;
    }

    public Dashboard dashboard(long userId) {
        activity.ensureCurrentEntitlements();
        boolean admin = jdbc.sql("SELECT role='ADMIN' FROM app_user WHERE id=:user")
                .param("user", userId).query(Boolean.class).optional().orElse(false);
        List<Map<String, Object>> providerConfigs = admin ? jdbc.sql("""
            SELECT provider,model_name,enabled,key_fingerprint,updated_at
            FROM platform_ai_config ORDER BY provider
            """).query().listOfRows() : List.of();
        List<Map<String, Object>> secrets = jdbc.sql("""
            SELECT provider,key_fingerprint,key_label,consent_version,consented_at,updated_at
            FROM user_ai_secret WHERE user_id=:user ORDER BY provider
            """).param("user", userId).query().listOfRows();
        String configuredDefault = jdbc.sql("SELECT default_provider FROM platform_ai_setting WHERE id=1")
                .query(String.class).optional().orElse(null);
        boolean platformDefaultAvailable = configuredDefault != null && jdbc.sql(
                "SELECT enabled FROM platform_ai_config WHERE provider=:provider")
                .param("provider", configuredDefault).query(Boolean.class).optional().orElse(false);
        boolean hermesPlatformAvailable = jdbc.sql(
                "SELECT enabled FROM platform_ai_config WHERE provider='hermes'")
                .query(Boolean.class).optional().orElse(false);
        return new Dashboard(YearMonth.now().toString(), activity.currentMonthScore(userId),
                activity.hasPlatformEntitlement(userId), activity.currentMonthLeaderboard(),
                admin ? skills.available() : List.of(), gateway.availableModelProviders(), providerConfigs, secrets,
                cipher.available(), admin ? configuredDefault : null,
                platformDefaultAvailable, hermesPlatformAvailable);
    }

    @Transactional
    public Map<String, Object> saveUserKey(long userId, SaveUserKey request) {
        String provider = gateway.require(request.provider()).id();
        if (!request.consentToStore()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "保存 API Key 前必须获得用户明确同意");
        if (!cipher.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境未配置 API Key 加密主密钥，只能使用不保存模式");
        char[] apiKey = requiredApiKey(request.apiKey());
        try {
            var encrypted = cipher.encrypt(apiKey);
            jdbc.sql("""
                INSERT INTO user_ai_secret(user_id,provider,encrypted_key,encryption_iv,key_fingerprint,key_label,consent_version)
                VALUES (:user,:provider,:encrypted,:iv,:fingerprint,:label,:consent)
                ON DUPLICATE KEY UPDATE encrypted_key=:encrypted,encryption_iv=:iv,key_fingerprint=:fingerprint,
                  key_label=:label,consent_version=:consent,consented_at=NOW(),updated_at=NOW()
                """).param("user", userId).param("provider", provider)
                    .param("encrypted", encrypted.ciphertext()).param("iv", encrypted.iv())
                    .param("fingerprint", encrypted.fingerprint()).param("label", cleanLabel(request.label()))
                    .param("consent", CONSENT_VERSION).update();
            return Map.of("provider", provider, "fingerprint", encrypted.fingerprint(), "saved", true);
        } finally { Arrays.fill(apiKey, '\0'); }
    }

    public void deleteUserKey(long userId, String provider) {
        jdbc.sql("DELETE FROM user_ai_secret WHERE user_id=:user AND provider=:provider")
                .param("user", userId).param("provider", gateway.require(provider).id()).update();
    }

    @Transactional
    public Map<String, Object> savePlatformKey(long adminId, SavePlatformKey request) {
        String provider = gateway.require(request.provider()).id();
        if (!cipher.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "当前环境未配置 API Key 加密主密钥");
        char[] apiKey = requiredApiKey(request.apiKey());
        try {
            var encrypted = cipher.encrypt(apiKey);
            jdbc.sql("""
                INSERT INTO platform_ai_config(provider,encrypted_key,encryption_iv,key_fingerprint,model_name,enabled,updated_by)
                VALUES (:provider,:encrypted,:iv,:fingerprint,:model,:enabled,:admin)
                ON DUPLICATE KEY UPDATE encrypted_key=:encrypted,encryption_iv=:iv,key_fingerprint=:fingerprint,
                  model_name=:model,enabled=:enabled,updated_by=:admin,updated_at=NOW()
                """).param("provider", provider).param("encrypted", encrypted.ciphertext()).param("iv", encrypted.iv())
                    .param("fingerprint", encrypted.fingerprint()).param("model", cleanModel(request.model()))
                    .param("enabled", request.enabled()).param("admin", adminId).update();
            return Map.of("provider", provider, "fingerprint", encrypted.fingerprint(), "enabled", request.enabled());
        } finally { Arrays.fill(apiKey, '\0'); }
    }

    @Transactional
    public Map<String,Object> savePlatformDefault(long adminId, PlatformDefaultRequest request) {
        String provider = gateway.require(request.provider()).id();
        if (gateway.require(provider).capabilities().contains("AGENT"))
            throw new IllegalArgumentException("外部 Agent 不能作为本地模型 Provider");
        boolean enabled = jdbc.sql("SELECT enabled FROM platform_ai_config WHERE provider=:provider")
                .param("provider", provider).query(Boolean.class).optional().orElse(false);
        if (!enabled) throw new IllegalArgumentException("请先配置并启用该 Provider");
        jdbc.sql("UPDATE platform_ai_setting SET default_provider=:provider,updated_by=:admin WHERE id=1")
                .param("provider", provider).param("admin", adminId).update();
        return Map.of("defaultProvider", provider);
    }

    public InvokeResult invoke(long userId, InvokeRequest request) {
        AiSkillPlanner.ExecutionPlan plan = orchestrator.prepare(request.skillId(), request.input(),
                courseContexts.load(request.courseId(), request.teacherId()));
        AiSkill skill = plan.primarySkill();
        AiCredentialSource source;
        try { source = AiCredentialSource.valueOf(request.credentialSource().toUpperCase()); }
        catch (Exception exception) { throw new IllegalArgumentException("不支持的凭据来源"); }

        String runtime = normalizeRuntime(request.runtime());
        try (ResolvedAiCredential credential = credentials.resolve(
                userId, runtime, request.provider(), request.model(), source, request.ephemeralApiKey());
             AiProviderAdapter.TransientImage image = images.decode(request.imageDataUrl())) {
            usageGuard.check(userId, source);
            if (image != null && !skill.modalities().contains("IMAGE")) throw new IllegalArgumentException("当前 Skill 不接受图片");
            String traceId = UUID.randomUUID().toString();
            long taskId = tasks.create(userId, skill.id(), credential.provider(), traceId, request.input());
            tasks.transition(taskId, AiTaskStatus.PLANNING, 0);
            long planningStep = tasks.startStep(taskId, 0, "SKILL");
            tasks.completeStep(planningStep, "skill=" + skill.id());
            tasks.transition(taskId, AiTaskStatus.EXECUTING, 1);
            Long modelStep = tasks.startStep(taskId, 1,
                    "hermes".equals(credential.provider()) ? "AGENT" : "MODEL");
            jdbc.sql("""
                INSERT INTO ai_usage_log(user_id,provider,model_name,skill_id,credential_source,status,input_units,trace_id)
                VALUES (:user,:provider,:model,:skill,:source,'ACCEPTED',:inputUnits,:trace)
                """).param("user", userId).param("provider", credential.provider()).param("model", credential.model())
                    .param("skill", skill.id()).param("source", source.name()).param("inputUnits", request.input().length())
                    .param("trace", traceId).update();
            try {
                var result = image != null && "math-problem-image-analysis".equals(skill.id())
                        && "deepseek".equals(credential.provider())
                        ? invokeVisionPipeline(userId, request.input(), credential, image)
                        : gateway.invoke(credential.provider(), credential.model(), plan, credential.apiKey(), image);
                jdbc.sql("""
                    UPDATE ai_usage_log SET status='SUCCEEDED',input_units=:input,output_units=:output,completed_at=NOW()
                    WHERE trace_id=:trace
                    """).param("input", result.inputUnits()).param("output", result.outputUnits()).param("trace", traceId).update();
                tasks.completeStep(modelStep, result.content());
                tasks.complete(taskId, result.content());
                return new InvokeResult(taskId, result.content(), traceId, AiTaskStatus.COMPLETED.name(),
                        result.preview(), skill.id(), credential.provider());
            } catch (RuntimeException exception) {
                jdbc.sql("UPDATE ai_usage_log SET status='FAILED',error_code='PROVIDER_ERROR',completed_at=NOW() WHERE trace_id=:trace")
                        .param("trace", traceId).update();
                tasks.fail(taskId, modelStep, exception);
                throw exception;
            }
        }
    }

    private String normalizeRuntime(String value) {
        if (value == null || value.isBlank() || "FINALS_COMPASS".equalsIgnoreCase(value)) return "FINALS_COMPASS";
        if ("HERMES".equalsIgnoreCase(value)) return "HERMES";
        throw new IllegalArgumentException("不支持的 Agent Runtime");
    }

    public Map<String,Object> task(long userId, long taskId) {
        return tasks.find(userId, taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 任务不存在"));
    }

    public List<Map<String,Object>> taskSteps(long userId, long taskId) {
        task(userId, taskId);
        return tasks.steps(userId, taskId);
    }

    private AiProviderAdapter.AiProviderResult invokeVisionPipeline(long userId, String input,
            ResolvedAiCredential credential, AiProviderAdapter.TransientImage image) {
        AiVisionProblemPipeline.PipelineResult result = visionPipeline.invoke(userId, input, credential, image);
        return new AiProviderAdapter.AiProviderResult(result.content(), result.inputUnits(), result.outputUnits(), false);
    }

    private char[] requiredApiKey(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("API Key 不能为空");
        return value.toCharArray();
    }

    private String cleanLabel(String value) { return value == null || value.isBlank() ? null : value.trim().substring(0, Math.min(80, value.trim().length())); }
    private String cleanModel(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("模型名称不能为空");
        return value.trim().substring(0, Math.min(120, value.trim().length()));
    }

    public record Dashboard(String month, int myScore, boolean platformEligible,
                            List<Map<String, Object>> leaderboard, List<AiSkillRegistry.SkillInfo> skills,
                            List<AiProviderGateway.ProviderInfo> providers,
                            List<Map<String, Object>> platformProviders, List<Map<String, Object>> savedKeys,
                            boolean encryptedStorageAvailable, String defaultProvider,
                            boolean platformDefaultAvailable, boolean hermesPlatformAvailable) {}
    public record SaveUserKey(String provider, String apiKey, String label, boolean consentToStore) {}
    public record SavePlatformKey(String provider, String model, String apiKey, boolean enabled) {}
    public record PlatformDefaultRequest(String provider) {}
    public record InvokeRequest(String runtime, String provider, String model, String skillId,
                                String credentialSource, String ephemeralApiKey,
                                String input, String imageDataUrl, Long courseId, Long teacherId) {}
    public record InvokeResult(long taskId, String content, String traceId, String status,
                               boolean preview, String skillId, String provider) {}
}
