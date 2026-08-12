package cn.finalscompass.ai.runtime.trace;

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
