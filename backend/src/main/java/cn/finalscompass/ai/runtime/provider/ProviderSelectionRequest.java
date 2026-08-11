package cn.finalscompass.ai.runtime.provider;

import java.util.Set;

public record ProviderSelectionRequest(
        Set<String> requiredCapabilities, int minimumContextWindow, int minimumOutputUnits,
        boolean structuredOutputRequired, boolean toolCallingRequired,
        Set<RuntimeProviderType> allowedProviderTypes, Set<String> allowedProviderKeys,
        String credentialSource
) {
    public ProviderSelectionRequest {
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        allowedProviderTypes = Set.copyOf(allowedProviderTypes);
        allowedProviderKeys = Set.copyOf(allowedProviderKeys);
    }
}
