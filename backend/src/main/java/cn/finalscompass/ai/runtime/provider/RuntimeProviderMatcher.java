package cn.finalscompass.ai.runtime.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/*
 *              ProviderSelectionRequest
 *                       │
 *                       ▼
 *                 参数合法性校验
 *                       │
 *                       ▼
 *        RuntimeProviderDefinitionRepository
 *                 查询可路由 Provider
 *                       │
 *          ┌────────────┼────────────┐
 *          ▼            ▼            ▼
 *     Provider 类型   凭据来源    指定 Provider
 *          │            │            │
 *          └────────────┼────────────┘
 *                       ▼
 *             模型能力和资源限制匹配
 *                       │
 *                       ▼
 *        Provider + Model + Endpoint
 *                       │
 *                       ▼
 *                 按路由规则排序
 */
/**
 * 根据能力、上下文窗口、模态、工具和凭据来源筛选并排序模型候选项。
 * 维护入口：路由硬约束和候选排序改这里；最终评分决策改 AiRuntimeRouterService。
 */
@Component
public final class RuntimeProviderMatcher {
  // 能力名称格式：必须以大写字母开头，只允许大写字母、数字和下划线，长度为 2-64
  private static final Pattern CAPABILITY = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
  // 系统支持的平台凭据、已保存的用户凭据和本次请求临时传入的用户凭据
  private static final Set<String> CREDENTIAL_SOURCES =
      Set.of("PLATFORM", "STORED_BYOK", "EPHEMERAL_BYOK");

  // 声明
  private final RuntimeProviderDefinitionRepository providers;

  // 注入
  public RuntimeProviderMatcher(RuntimeProviderDefinitionRepository providers) {
    this.providers = providers;
  }

  // 整个类真正的入口：根据调用要求筛选 Provider、Model 和 Endpoint，组装候选项并按路由规则排序
  public List<RuntimeProviderCandidate> match(ProviderSelectionRequest request) {
    // 检查上下文限制、输出限制、凭据来源和能力名称是否合法
    validate(request);
    // 只查询允许参与路由的 Provider
    return providers.findRoutable().stream()
        // 调用方没有限制 Provider 类型时全部保留，否则只保留指定类型
        .filter(
            provider ->
                request.allowedProviderTypes().isEmpty()
                    || request.allowedProviderTypes().contains(provider.type()))
        // 调用方没有指定 Provider 时全部保留，否则只保留指定 Provider
        .filter(
            provider ->
                request.allowedProviderKeys().isEmpty()
                    || request.allowedProviderKeys().contains(provider.key()))
        // Provider 必须支持当前 API Key 的凭据来源
        .filter(
            provider -> provider.supportedCredentialSources().contains(request.credentialSource()))
        // 从 Provider 中继续筛选满足要求的模型，再与它的每个 Endpoint 组合成一个候选项
        .flatMap(
            provider ->
                provider.models().stream()
                    .filter(model -> supports(model, request))
                    .flatMap(
                        model ->
                            provider.endpoints().stream()
                                .map(
                                    endpoint ->
                                        new RuntimeProviderCandidate(provider, model, endpoint))))
        // 候选项按状态、优先级、权重和唯一 Key 排序，排在前面的优先调用
        .sorted(candidateOrder())
        .toList();
  }

  // 判断模型是否同时满足能力、结构化输出、工具调用、上下文窗口和最大输出量要求
  private boolean supports(RuntimeProviderModel model, ProviderSelectionRequest request) {
    return model.capabilities().containsAll(request.requiredCapabilities())
        && (!request.structuredOutputRequired() || model.structuredOutput())
        && (!request.toolCallingRequired() || model.toolCalling())
        && (request.minimumContextWindow() == 0
            || model.contextWindow() != null
                && model.contextWindow() >= request.minimumContextWindow())
        && (request.minimumOutputUnits() == 0
            || model.maxOutputUnits() != null
                && model.maxOutputUnits() >= request.minimumOutputUnits());
  }

  // 定义候选模型的路由顺序：状态和 priority 越小越优先，weight 越大越优先，最后用 Key 保证排序结果稳定
  private Comparator<RuntimeProviderCandidate> candidateOrder() {
    return Comparator
        // 优先使用 ACTIVE 的 Provider 和 Endpoint
        .comparingInt((RuntimeProviderCandidate value) -> statusRank(value.provider().status()))
        .thenComparingInt(value -> statusRank(value.endpoint().status()))
        // 状态相同时，先比较模型和 Endpoint 的路由优先级
        .thenComparingInt(value -> value.model().routingPriority())
        .thenComparingInt(value -> value.endpoint().priority())
        // 优先级相同时，权重更大的模型和 Endpoint 排在前面
        .thenComparing(
            Comparator.comparingInt(
                    (RuntimeProviderCandidate value) -> value.model().routingWeight())
                .reversed())
        .thenComparing(
            Comparator.comparingInt((RuntimeProviderCandidate value) -> value.endpoint().weight())
                .reversed())
        // 前面的规则都相同时按 Key 排序，避免数据库返回顺序影响匹配结果
        .thenComparing(value -> value.provider().key())
        .thenComparing(value -> value.model().key())
        .thenComparing(value -> value.endpoint().key());
  }

  // 把状态转换成排序值：ACTIVE 为 0，其他状态为 1，所以 ACTIVE 会排在前面
  private int statusRank(RuntimeProviderStatus status) {
    return status == RuntimeProviderStatus.ACTIVE ? 0 : 1;
  }

  // 校验模型选择请求，防止非法限制条件进入 Provider 匹配流程
  private void validate(ProviderSelectionRequest request) {
    // 上下文窗口和最大输出量传 0 表示不限制，但不能传负数
    if (request == null || request.minimumContextWindow() < 0 || request.minimumOutputUnits() < 0)
      throw new IllegalArgumentException("Provider selection limits are invalid");
    // 凭据来源必须是系统支持的三种类型之一
    if (!CREDENTIAL_SOURCES.contains(request.credentialSource()))
      throw new IllegalArgumentException("Provider credential source is invalid");
    // 所有必需能力都必须符合统一的能力名称格式
    if (!request.requiredCapabilities().stream()
        .allMatch(value -> CAPABILITY.matcher(value).matches()))
      throw new IllegalArgumentException("Provider capability is invalid");
  }
}
