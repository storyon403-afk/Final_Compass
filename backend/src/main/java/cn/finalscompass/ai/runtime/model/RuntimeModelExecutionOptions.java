package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;
import java.util.Set;

/**
 * 运行时模型执行Options的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：统一模型命令、回退和执行结果契约变化时修改这里
 */
public record RuntimeModelExecutionOptions(
    String credentialSource,
    Set<RuntimeProviderType> allowedProviderTypes,
    Set<String> allowedProviderKeys,
    Set<String> requestedTools,
    int minimumContextWindow,
    int minimumOutputUnits,
    boolean structuredOutputRequired,
    boolean toolCallingRequired) {
  public RuntimeModelExecutionOptions {
    allowedProviderTypes = Set.copyOf(allowedProviderTypes);
    allowedProviderKeys = Set.copyOf(allowedProviderKeys);
    requestedTools = Set.copyOf(requestedTools);
  }
}
