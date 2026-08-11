package cn.finalscompass.ai.runtime.provider;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public final class RuntimeProviderMatcher {
    private static final Pattern CAPABILITY = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    private static final Set<String> CREDENTIAL_SOURCES =
            Set.of("PLATFORM", "STORED_BYOK", "EPHEMERAL_BYOK");
    private final RuntimeProviderDefinitionRepository providers;

    public RuntimeProviderMatcher(RuntimeProviderDefinitionRepository providers) { this.providers = providers; }

    public List<RuntimeProviderCandidate> match(ProviderSelectionRequest request) {
        validate(request);
        return providers.findRoutable().stream()
                .filter(provider -> request.allowedProviderTypes().isEmpty()
                        || request.allowedProviderTypes().contains(provider.type()))
                .filter(provider -> request.allowedProviderKeys().isEmpty()
                        || request.allowedProviderKeys().contains(provider.key()))
                .filter(provider -> provider.supportedCredentialSources().contains(request.credentialSource()))
                .flatMap(provider -> provider.models().stream()
                        .filter(model -> supports(model, request))
                        .flatMap(model -> provider.endpoints().stream()
                                .map(endpoint -> new RuntimeProviderCandidate(provider, model, endpoint))))
                .sorted(candidateOrder())
                .toList();
    }

    private boolean supports(RuntimeProviderModel model, ProviderSelectionRequest request) {
        return model.capabilities().containsAll(request.requiredCapabilities())
                && (!request.structuredOutputRequired() || model.structuredOutput())
                && (!request.toolCallingRequired() || model.toolCalling())
                && (request.minimumContextWindow() == 0
                    || model.contextWindow() != null && model.contextWindow() >= request.minimumContextWindow())
                && (request.minimumOutputUnits() == 0
                    || model.maxOutputUnits() != null && model.maxOutputUnits() >= request.minimumOutputUnits());
    }

    private Comparator<RuntimeProviderCandidate> candidateOrder() {
        return Comparator
                .comparingInt((RuntimeProviderCandidate value) -> statusRank(value.provider().status()))
                .thenComparingInt(value -> statusRank(value.endpoint().status()))
                .thenComparingInt(value -> value.model().routingPriority())
                .thenComparingInt(value -> value.endpoint().priority())
                .thenComparing(Comparator.comparingInt(
                        (RuntimeProviderCandidate value) -> value.model().routingWeight()).reversed())
                .thenComparing(Comparator.comparingInt(
                        (RuntimeProviderCandidate value) -> value.endpoint().weight()).reversed())
                .thenComparing(value -> value.provider().key())
                .thenComparing(value -> value.model().key())
                .thenComparing(value -> value.endpoint().key());
    }

    private int statusRank(RuntimeProviderStatus status) {
        return status == RuntimeProviderStatus.ACTIVE ? 0 : 1;
    }

    private void validate(ProviderSelectionRequest request) {
        if (request == null || request.minimumContextWindow() < 0 || request.minimumOutputUnits() < 0)
            throw new IllegalArgumentException("Provider selection limits are invalid");
        if (!CREDENTIAL_SOURCES.contains(request.credentialSource()))
            throw new IllegalArgumentException("Provider credential source is invalid");
        if (!request.requiredCapabilities().stream().allMatch(value -> CAPABILITY.matcher(value).matches()))
            throw new IllegalArgumentException("Provider capability is invalid");
    }
}
