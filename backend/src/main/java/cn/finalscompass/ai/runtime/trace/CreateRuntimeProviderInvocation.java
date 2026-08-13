package cn.finalscompass.ai.runtime.trace;

/**
 * 创建参数运行时供应商调用的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：执行链路、状态和审计字段变化时修改这里。
 */
public record CreateRuntimeProviderInvocation(
    String invocationId,
    long executionNodeId,
    long providerId,
    long providerModelId,
    String providerKeySnapshot,
    String modelKeySnapshot,
    RuntimeCredentialSource credentialSource,
    int attempt,
    Long fallbackFromId,
    String metadataJson) {}
