package cn.finalscompass.ai.runtime.provider;

import java.util.Set;

/**
 * 供应商选择请求的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：供应商、模型、端点定义及匹配规则变化时修改这里。
 */
public record ProviderSelectionRequest(
    Set<String> requiredCapabilities,
    int minimumContextWindow,
    int minimumOutputUnits,
    boolean structuredOutputRequired,
    boolean toolCallingRequired,
    Set<RuntimeProviderType> allowedProviderTypes,
    Set<String> allowedProviderKeys,
    String credentialSource) {
  public ProviderSelectionRequest {
    requiredCapabilities = Set.copyOf(requiredCapabilities);
    allowedProviderTypes = Set.copyOf(allowedProviderTypes);
    allowedProviderKeys = Set.copyOf(allowedProviderKeys);
  }
}
