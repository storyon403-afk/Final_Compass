package cn.finalscompass.ai.runtime.provider;

import java.util.List;
import java.util.Set;

public record RuntimeProviderDefinition(
        long id, String key, String name, RuntimeProviderType type, String adapterKey,
        RuntimeProviderStatus status, Set<String> supportedCredentialSources,
        String credentialPolicyJson, String configurationJson,
        List<RuntimeProviderEndpoint> endpoints, List<RuntimeProviderModel> models
) {
    public RuntimeProviderDefinition {
        supportedCredentialSources = Set.copyOf(supportedCredentialSources);
        endpoints = List.copyOf(endpoints);
        models = List.copyOf(models);
    }
}
