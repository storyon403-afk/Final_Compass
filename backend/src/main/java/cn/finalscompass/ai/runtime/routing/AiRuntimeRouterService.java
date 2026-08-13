package cn.finalscompass.ai.runtime.routing;

import cn.finalscompass.ai.runtime.trace.RuntimeType;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/*
 * 维护流程图：
 *   RouteRequest --> ProviderMatcher --> Candidates --> 权重评分
 *       --> RouteDecision（主模型、备用模型、选择原因）
 */
/**
 * 对外提供运行时能力卡片，并结合候选模型生成最终路由决策。
 * 维护入口：新增 Runtime 类型改能力卡片；供应商筛选改 Matcher；评分权重改 decide。
 */
@Service
public final class AiRuntimeRouterService {
  private static final Set<String> WEB_AGENT_HINTS =
      Set.of("多网页", "多个网页", "web agent", "webagent", "kimi", "qwen", "通义", "网页协作");
  private static final Set<String> AGENT_HINTS =
      Set.of(
          "自主", "调研", "研究", "拆解", "规划并执行", "开放任务", "生成", "文件", "文档", "pdf", "ppt", "docx", "xlsx",
          "海报", "agent");

  // 加载完整供应商目录。
  public Catalog catalog() {
    return new Catalog(
        "AI_CENTER",
        List.of(
            new RuntimeCard(
                RuntimeType.CHAT,
                "Chat Runtime",
                "基于知识库 RAG 直接回答问题，不生成文件",
                "AVAILABLE",
                0,
                List.of("Knowledge Service", "RAG"),
                null),
            new RuntimeCard(
                RuntimeType.AGENT,
                "Agent Runtime",
                "通过 Agent Gateway 调用本地 Agent 完成文件生成等复杂任务",
                "FOUNDATION",
                0,
                List.of(
                    "Agent Gateway",
                    "Knowledge Service",
                    "MCP Tool",
                    "Browser Gateway",
                    "Document Generation"),
                "需要本地 Agent Gateway 已启动"),
            new RuntimeCard(
                RuntimeType.MULTI_WEB_AGENT,
                "Multi-WebAgent Runtime",
                "多个网页 Agent 协作并由平台 Critic 融合审核",
                "EXPERIMENTAL",
                0,
                List.of("Chrome Extension", "Web Agent Adapters", "Critic Agent"),
                "仅支持已安装扩展且已登录目标网站的桌面 Chrome")));
  }

  // 为请求选择合适的供应商和模型。
  public RouteDecision route(RouteRequest request) {
    if (request == null
        || request.goal() == null
        || request.goal().isBlank()
        || request.goal().length() > 20_000)
      throw new IllegalArgumentException("Runtime routing goal is invalid");
    Set<String> capabilities =
        request.clientCapabilities() == null ? Set.of() : request.clientCapabilities();
    RuntimeType selected = preferred(request.preferredRuntime());
    String normalized = request.goal().toLowerCase(Locale.ROOT);
    if (selected == null) {
      selected =
          contains(normalized, WEB_AGENT_HINTS)
              ? RuntimeType.MULTI_WEB_AGENT
              : contains(normalized, AGENT_HINTS) ? RuntimeType.AGENT : RuntimeType.CHAT;
    }
    if (selected == RuntimeType.MULTI_WEB_AGENT && !capabilities.contains("CHROME_EXTENSION"))
      return new RouteDecision(
          RuntimeType.AGENT,
          "AGENT_FALLBACK",
          "当前客户端未连接 Chrome Extension，已回退到 Agent Runtime",
          List.of(),
          List.of("CHROME_EXTENSION"));
    if (selected == RuntimeType.AGENT)
      return new RouteDecision(
          selected,
          "AGENT_GATEWAY",
          "目标需要本地 Agent 自主执行或生成文件",
          List.of("KNOWLEDGE_READ"),
          List.of("EXTERNAL_AGENT_GATEWAY"));
    if (selected == RuntimeType.MULTI_WEB_AGENT)
      return new RouteDecision(
          selected,
          "MULTI_WEB_AGENT",
          "目标明确要求多个网页 Agent 协作",
          List.of("BROWSER_CONTROL"),
          List.of("CHROME_EXTENSION", "SIGNED_IN_WEB_AGENTS"));
    return new RouteDecision(selected, "CHAT", "目标适合直接问答，结合知识库检索回答", List.of(), List.of());
  }

  // 选择用户首选或平台默认配置。局部失败会降级为空结果，不让辅助能力中断主流程。
  private RuntimeType preferred(String value) {
    if (value == null || value.isBlank() || "AUTO".equalsIgnoreCase(value)) return null;
    try {
      RuntimeType runtime = RuntimeType.valueOf(value);
      if (runtime == RuntimeType.LEGACY || runtime == RuntimeType.WORKFLOW)
        throw new IllegalArgumentException();
      return runtime;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Preferred Runtime is invalid");
    }
  }

  private boolean contains(String text, Set<String> hints) {
    return hints.stream().anyMatch(text::contains);
  }

  public record Catalog(String entrypoint, List<RuntimeCard> runtimes) {}

  public record RuntimeCard(
      RuntimeType type,
      String name,
      String description,
      String status,
      int publishedDefinitions,
      List<String> capabilities,
      String requirement) {}

  public record RouteRequest(
      String goal, String preferredRuntime, Set<String> clientCapabilities) {}

  public record RouteDecision(
      RuntimeType runtimeType,
      String runtimeDefinitionKey,
      String reason,
      List<String> requiredPermissions,
      List<String> requiredClientCapabilities) {}
}
