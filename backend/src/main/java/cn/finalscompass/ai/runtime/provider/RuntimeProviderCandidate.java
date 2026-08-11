package cn.finalscompass.ai.runtime.provider;

public record RuntimeProviderCandidate(
        RuntimeProviderDefinition provider, RuntimeProviderModel model,
        RuntimeProviderEndpoint endpoint
) {}
