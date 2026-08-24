package cn.finalscompass.ai.runtime.provider;

import java.util.List;
import java.util.Set;

/**
 * 运行时供应商定义的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：供应商、模型、端点定义及匹配规则变化时修改这里
 */
public record RuntimeProviderDefinition(
    long id,
    String key,
    String name,
    RuntimeProviderType type,
    String adapterKey,
    RuntimeProviderStatus status,
    Set<String> supportedCredentialSources,
    String credentialPolicyJson,
    String configurationJson,
    List<RuntimeProviderEndpoint> endpoints,
    List<RuntimeProviderModel> models) {
  public RuntimeProviderDefinition {
    supportedCredentialSources = Set.copyOf(supportedCredentialSources);
    endpoints = List.copyOf(endpoints);
    models = List.copyOf(models);
  }
}
