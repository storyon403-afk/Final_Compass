package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;

import java.util.Set;

public record RuntimeModelExecutionOptions(
        String credentialSource, Set<RuntimeProviderType> allowedProviderTypes,
        Set<String> allowedProviderKeys, Set<String> requestedTools,
        int minimumContextWindow, int minimumOutputUnits,
        boolean structuredOutputRequired, boolean toolCallingRequired
) {
    public RuntimeModelExecutionOptions {
        allowedProviderTypes = Set.copyOf(allowedProviderTypes);
        allowedProviderKeys = Set.copyOf(allowedProviderKeys);
        requestedTools = Set.copyOf(requestedTools);
    }
}
