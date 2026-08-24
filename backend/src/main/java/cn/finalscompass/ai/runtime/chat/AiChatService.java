package cn.finalscompass.ai.runtime.chat;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.runtime.knowledge.KnowledgeService;
import cn.finalscompass.ai.runtime.model.RuntimeModelClientGateway;
import cn.finalscompass.ai.runtime.model.RuntimeModelDispatch;
import cn.finalscompass.ai.runtime.model.RuntimeModelExecutionResult;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import cn.finalscompass.ai.runtime.provider.ProviderSelectionRequest;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderCandidate;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderMatcher;
import cn.finalscompass.ai.runtime.trace.CreateRuntimeExecution;
import cn.finalscompass.ai.runtime.trace.CreateRuntimeExecutionNode;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionNodeStatus;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionNodeType;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionStatus;
import cn.finalscompass.ai.runtime.trace.RuntimeExecutionTraceStore;
import cn.finalscompass.ai.runtime.trace.RuntimeType;
import cn.finalscompass.config.TraceContext;
import cn.finalscompass.service.AiCredentialResolver;
import cn.finalscompass.service.AiUsageGuardService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/*
 *                     AiChatService
 *                        │
 *         ┌──────────────┼──────────────┐
 *         │              │              │
 *         ▼              ▼              ▼
 * KnowledgeService RuntimeProviderMatcher  CredentialResolver
 *      RAG              模型选择           Key解析
 *         │              │              │
 *         └──────────────┼──────────────┘
 *                        ▼
 *             RuntimeModelClientGateway
 *                   调用大模型
 *                        │
 *             ┌──────────┴─────────┐
 *             ▼                    ▼
 *       TraceStore              Redis
 *       执行记录             对话上下文
 *             │
 *             └──────────┬─────────┘
 *                        ▼
 *                   SseEmitter
 *                   返回前端
 */

/**
 * AI 中心轻量聊天入口：组合知识库 RAG、模型选择、凭据解析、执行追踪和 SSE 返回，不生成文件
 * 维护入口：聊天编排和历史上下文改这里；检索改 KnowledgeService；模型回退改 ModelClientGateway
 */
@Service
public final class AiChatService {
  private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
  private static final int HISTORY_LIMIT = 20;
  private static final Duration HISTORY_TTL = Duration.ofMinutes(120);

  // 声明
  private final KnowledgeService knowledge;
  private final RuntimeProviderMatcher matcher;
  private final AiCredentialResolver credentials;
  private final RuntimeModelClientGateway models;
  private final RuntimeExecutionTraceStore traces;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final AiUsageGuardService usage;

  // 注入
  public AiChatService(
      KnowledgeService knowledge,
      RuntimeProviderMatcher matcher,
      AiCredentialResolver credentials,
      RuntimeModelClientGateway models,
      RuntimeExecutionTraceStore traces,
      StringRedisTemplate redis,
      ObjectMapper json,
      AiUsageGuardService usage) {
    this.knowledge = knowledge;
    this.matcher = matcher;
    this.credentials = credentials;
    this.models = models;
    this.traces = traces;
    this.redis = redis;
    this.json = json;
    this.usage = usage;
  }

  // 创建一个聊天 Session ID
  public String createSession() {
    return UUID.randomUUID().toString();
  }

  // 请求 DTO
  public record ChatRequest(
      String message,
      String credentialSource,
      String provider,
      String model,
      String ephemeralApiKey,
      String credentialPurpose,
      Boolean useKnowledge) {}

  // 整个类真正的入口
  public void answer(long userId, String sessionKey, ChatRequest request, SseEmitter emitter) {
    String message = request == null ? null : request.message();
    // 检查输入
    if (sessionKey == null
        || sessionKey.isBlank()
        || message == null
        || message.isBlank()
        || message.length() > 80_000) {
      send(emitter, "error", Map.of("code", "INVALID_MESSAGE", "message", "聊天内容无效"));
      return;
    }
    // 确定凭据来源
    AiCredentialSource source = parseSource(request.credentialSource());
    long executionId = 0;
    long nodeId = 0;
    String aiTraceId = null;
    boolean checked = false;
    try {
      usage.check(userId, source);
      checked = true;
      boolean internalMultiWeb =
          request.credentialPurpose() != null
              && request.credentialPurpose().startsWith("MULTIWEB_");
      List<KnowledgeService.SearchResult> sources =
          internalMultiWeb && !Boolean.TRUE.equals(request.useKnowledge())
              ? List.of()
              : knowledge.search(userId, null, truncate(message, 500), 5); // RAG 搜索
      send(
          emitter,
          "sources",
          Map.of(
              "sources",
              sources.stream()
                  .map(
                      item ->
                          Map.of(
                              "sourceKey", nullSafe(item.sourceKey()),
                              "title", nullSafe(item.title()),
                              "heading", nullSafe(item.heading()),
                              "score", item.score()))
                  .toList()));

      // 创建 Trace
      aiTraceId = UUID.randomUUID().toString();
      MDC.put(TraceContext.AI_TRACE_ID, aiTraceId);
      String httpTraceId = TraceContext.currentTraceId();
      executionId =
          traces.createExecution(
              new CreateRuntimeExecution(
                  UUID.randomUUID().toString(),
                  aiTraceId,
                  null,
                  null,
                  userId,
                  sessionKey,
                  RuntimeType.CHAT,
                  truncate(message, 200),
                  null,
                  null,
                  null,
                  httpTraceId == null
                      ? "{}"
                      : json.writeValueAsString(Map.of("httpTraceId", httpTraceId))));
      nodeId =
          traces.createNode(
              new CreateRuntimeExecutionNode(
                  executionId,
                  null,
                  "answer",
                  RuntimeExecutionNodeType.MODEL,
                  null,
                  null,
                  null,
                  null,
                  1,
                  null,
                  null,
                  "{}"));
      traces.transitionExecution(executionId, RuntimeExecutionStatus.RUNNING, null, null, null);
      traces.transitionNode(nodeId, RuntimeExecutionNodeStatus.READY, null, null, null, null);
      traces.transitionNode(nodeId, RuntimeExecutionNodeStatus.RUNNING, null, null, null, null);

      var credential =
          "MULTIWEB_REVIEW".equals(request.credentialPurpose())
              ? credentials.resolveUserReview(
                  userId,
                  blankToNull(request.provider()),
                  blankToNull(request.model()),
                  source,
                  blankToNull(request.ephemeralApiKey()))
              : credentials.resolve(
                  userId,
                  "CHAT",
                  blankToNull(request.provider()), // 解析 API Key
                  blankToNull(request.model()),
                  source,
                  blankToNull(request.ephemeralApiKey()));

      // Provider Matcher(匹配模型)
      List<RuntimeProviderCandidate> candidates =
          matcher.match(
              new ProviderSelectionRequest(
                  Set.of(),
                  0,
                  0,
                  false,
                  false,
                  Set.of(),
                  Set.of(credential.provider()),
                  source.name()));
      List<RuntimeProviderCandidate> matching =
          candidates.stream()
              .filter(candidate -> candidate.model().key().equals(credential.model()))
              .toList();
      if (matching.isEmpty())
        throw new IllegalStateException(
            "未找到可用的模型候选：" + credential.provider() + "/" + credential.model());
      RuntimeProviderCandidate primary = matching.get(0);
      List<RuntimeModelInvocationCommand> fallbacks =
          matching.stream()
              .skip(1)
              .limit(2)
              .map(candidate -> command(candidate, source, userId, sessionKey, message, sources))
              .toList();

      // 生成模型调用 Command
      RuntimeModelInvocationCommand command =
          command(primary, source, userId, sessionKey, message, sources);
      // 调用模型
      RuntimeModelExecutionResult result =
          models.execute(
              nodeId,
              new RuntimeModelDispatch("LLM_PROMPT", command, fallbacks),
              lease ->
                  "MULTIWEB_REVIEW".equals(request.credentialPurpose())
                      ? credentials.resolveUserReview(
                          userId,
                          lease.providerKey(),
                          lease.modelKey(),
                          source,
                          blankToNull(request.ephemeralApiKey()))
                      : credentials.resolve(
                          userId,
                          "CHAT",
                          lease.providerKey(),
                          lease.modelKey(),
                          source,
                          blankToNull(request.ephemeralApiKey())),
              null);

      // 处理返回结果
      String answer =
          result.content() == null || result.content().isBlank() ? "（模型没有返回内容）" : result.content();
      send(emitter, "delta", Map.of("text", answer));
      send(
          emitter,
          "done",
          Map.of(
              "traceId",
              aiTraceId,
              "provider",
              nullSafe(result.providerKey()),
              "model",
              nullSafe(result.modelKey()),
              "inputUnits",
              result.inputUnits(),
              "outputUnits",
              result.outputUnits()));
      // 保存历史
      appendHistory(userId, sessionKey, message, answer);
      usage.record(userId, result.providerKey(), result.modelKey(), "CHAT", source, true,
          result.inputUnits(), result.outputUnits(), null, String.valueOf(executionId));
      // 更新 Trace
      traces.transitionNode(nodeId, RuntimeExecutionNodeStatus.SUCCEEDED, null, null, null, null);
      traces.transitionExecution(executionId, RuntimeExecutionStatus.SUCCEEDED, null, null, null);
      // 完成 SSE
      emitter.complete();
    } catch (Exception failure) {
      log.error("AI chat execution failed", failure);
      if (checked) usage.record(userId, request.provider(), request.model(), "CHAT", source, false,
          0, 0, failure.getClass().getSimpleName(), executionId > 0 ? String.valueOf(executionId) : null);
      // 更新 Trace
      if (nodeId > 0) safeTransitionNode(nodeId);
      if (executionId > 0)
        traces.transitionExecution(
            executionId, RuntimeExecutionStatus.FAILED, null, "CHAT_FAILED", summarize(failure));
      send(
          emitter,
          "error",
          Map.of(
              "code", "CHAT_FAILED",
              "message", summarize(failure),
              "traceId", nullSafe(aiTraceId)));
      emitter.complete();
    } finally {
      MDC.remove(TraceContext.AI_TRACE_ID);
    }
  }

  /**
   * Candidate --> InvocationCommand: 候选模型+Prompt+用户上下文+价格+Endpoint+Timeout -->
   * RuntimeModelInvocationCommand /** 生成模型调用 Command
   *
   * @param candidate 模型候选者
   * @param source 凭据源
   * @param userId 用户 ID
   * @param sessionKey 会话 Key
   * @param message 消息
   * @param sources 源数据
   * @return 模型调用 Command
   */
  private RuntimeModelInvocationCommand command(
      RuntimeProviderCandidate candidate,
      AiCredentialSource source,
      long userId,
      String sessionKey,
      String message,
      List<KnowledgeService.SearchResult> sources) {
    var provider = candidate.provider();
    var model = candidate.model();
    var endpoint = candidate.endpoint();
    return new RuntimeModelInvocationCommand(
        provider.id(),
        provider.key(),
        provider.type(),
        provider.adapterKey(),
        model.id(),
        model.key(),
        endpoint.id(),
        endpoint.key(),
        endpoint.baseUrl(),
        source.name(),
        "chat",
        "1.0",
        systemInstruction(sources),
        userPrompt(userId, sessionKey, message),
        null,
        null,
        null,
        Set.of(),
        List.of(),
        Set.of("TEXT"),
        false,
        model.inputUnitPrice(),
        model.outputUnitPrice(),
        model.currency(),
        endpoint.connectTimeoutMs(),
        endpoint.requestTimeoutMs());
  }

  // 负责构造 system prompt
  private String systemInstruction(List<KnowledgeService.SearchResult> sources) {
    StringBuilder builder =
        new StringBuilder("你是 Finals Compass 的学习助手，只回答用户问题，不生成文件。回答使用简体中文，条理清晰。");
    if (sources != null && !sources.isEmpty()) {
      builder.append("\n\n以下是从知识库检索到的资料，请优先依据资料回答，并在引用处标注对应编号 [n]；资料之外的内容请说明是通用知识。\n");
      int index = 1;
      for (KnowledgeService.SearchResult item : sources) {
        builder.append("\n[").append(index++).append("] ").append(nullSafe(item.title()));
        if (item.heading() != null && !item.heading().isBlank())
          builder.append(" · ").append(item.heading());
        builder.append("\n").append(truncate(nullSafe(item.content()), 1200)).append("\n");
      }
    }
    return builder.toString();
  }

  // 把 Redis 中的历史对话 + 当前问题组合起来
  private String userPrompt(long userId, String sessionKey, String message) {
    List<Map<String, String>> history = loadHistory(userId, sessionKey);
    StringBuilder builder = new StringBuilder();
    if (!history.isEmpty()) {
      builder.append("## 对话历史\n");
      for (Map<String, String> item : history) {
        builder
            .append("user".equals(item.get("role")) ? "用户：" : "助手：")
            .append(nullSafe(item.get("content")))
            .append("\n");
      }
      builder.append("\n");
    }
    builder.append("## 当前问题\n").append(message);
    return builder.toString();
  }

  // 解析凭据来源
  private AiCredentialSource parseSource(String value) {
    if (value == null || value.isBlank()) return AiCredentialSource.PLATFORM;
    try {
      return AiCredentialSource.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("凭据来源无效");
    }
  }

  // 生成历史记录键
  private String historyKey(long userId, String sessionKey) {
    return "fc:chat:" + userId + ":" + sessionKey;
  }

  // Redis JSON --> List<Map<String,String>>
  private List<Map<String, String>> loadHistory(long userId, String sessionKey) {
    try {
      String stored = redis.opsForValue().get(historyKey(userId, sessionKey));
      if (stored == null || stored.isBlank()) return List.of();
      return json.readValue(stored, new TypeReference<List<Map<String, String>>>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }

  // 读取loadHistory()，追加当前对话，截断到 HISTORY_LIMIT，写回 Redis
  private void appendHistory(long userId, String sessionKey, String message, String answer) {
    try {
      List<Map<String, String>> history = new ArrayList<>(loadHistory(userId, sessionKey));
      history.add(Map.of("role", "user", "content", truncate(message, 4000)));
      history.add(Map.of("role", "assistant", "content", truncate(answer, 4000)));
      while (history.size() > HISTORY_LIMIT) history.remove(0);
      // 保存 120 分钟
      redis
          .opsForValue()
          .set(historyKey(userId, sessionKey), json.writeValueAsString(history), HISTORY_TTL);
    } catch (Exception ignored) {
    }
  }

  // 发送 SSE 事件
  private void send(SseEmitter emitter, String event, Map<String, ?> payload) {
    try {
      emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(payload)));
    } catch (IOException ignored) {
    }
  }

  // 安全地更新 Trace 节点状态为 FAILED，避免异常中断
  private void safeTransitionNode(long nodeId) {
    try {
      traces.transitionNode(
          nodeId, RuntimeExecutionNodeStatus.FAILED, null, null, "CHAT_FAILED", null);
    } catch (Exception ignored) {
    }
  }

  // 提取异常信息，并最多保留 500 字符
  private static String summarize(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? failure.getClass().getSimpleName()
        : truncate(message, 500);
  }

  // 截断字符串
  private static String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }

  // 避免NullPointerException：null --> ""
  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  // "" --> null:后面Resolver 判断
  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
