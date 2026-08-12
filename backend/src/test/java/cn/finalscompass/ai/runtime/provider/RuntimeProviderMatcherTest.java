package cn.finalscompass.ai.runtime.provider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeProviderMatcherTest {
    @Test
    void filtersHardRequirementsAndRanksHealthyCandidateFirst() {
        RuntimeProviderDefinition degradedVision = provider(
                1, "gemini", RuntimeProviderStatus.DEGRADED, Set.of("PLATFORM"),
                model(11, "vision-model", 50, 100, Set.of("VISION", "TEXT_REASONING"), 32000, true),
                endpoint(21, RuntimeProviderStatus.ACTIVE, 10, 100));
        RuntimeProviderDefinition activeVision = provider(
                2, "openai", RuntimeProviderStatus.ACTIVE, Set.of("PLATFORM", "EPHEMERAL_BYOK"),
                model(12, "vision-model", 100, 100, Set.of("VISION", "TEXT_REASONING"), 64000, true),
                endpoint(22, RuntimeProviderStatus.ACTIVE, 100, 100));
        RuntimeProviderMatcher matcher = new RuntimeProviderMatcher(
                new FakeRepository(List.of(degradedVision, activeVision)));

        var result = matcher.match(new ProviderSelectionRequest(
                Set.of("VISION"), 16000, 0, true, false,
                Set.of(RuntimeProviderType.API), Set.of(), "PLATFORM"));

        assertEquals(List.of("openai", "gemini"),
                result.stream().map(candidate -> candidate.provider().key()).toList());
    }

    @Test
    void excludesUnknownLimitsAndUnsupportedCredentialSource() {
        RuntimeProviderDefinition provider = provider(
                1, "local", RuntimeProviderStatus.ACTIVE, Set.of("PLATFORM"),
                model(1, "unknown-limits", 1, 1, Set.of("TEXT_REASONING"), null, false),
                endpoint(1, RuntimeProviderStatus.ACTIVE, 1, 1));
        RuntimeProviderMatcher matcher = new RuntimeProviderMatcher(new FakeRepository(List.of(provider)));

        assertEquals(0, matcher.match(new ProviderSelectionRequest(
                Set.of("TEXT_REASONING"), 1000, 0, false, false,
                Set.of(), Set.of(), "PLATFORM")).size());
        assertEquals(0, matcher.match(new ProviderSelectionRequest(
                Set.of("TEXT_REASONING"), 0, 0, false, false,
                Set.of(), Set.of(), "STORED_BYOK")).size());
    }

    @Test
    void rejectsInvalidCapabilityBeforeRepositoryAccess() {
        RuntimeProviderMatcher matcher = new RuntimeProviderMatcher(new FakeRepository(List.of()));
        assertThrows(IllegalArgumentException.class, () -> matcher.match(new ProviderSelectionRequest(
                Set.of("bad-capability"), 0, 0, false, false,
                Set.of(), Set.of(), "PLATFORM")));
    }

    private RuntimeProviderDefinition provider(long id, String key, RuntimeProviderStatus status,
                                               Set<String> credentials, RuntimeProviderModel model,
                                               RuntimeProviderEndpoint endpoint) {
        return new RuntimeProviderDefinition(id, key, key, RuntimeProviderType.API, "adapter-v1", status,
                credentials, "{}", "{}", List.of(endpoint), List.of(model));
    }

    private RuntimeProviderModel model(long id, String key, int priority, int weight,
                                       Set<String> capabilities, Integer context, boolean structured) {
        return new RuntimeProviderModel(id, key, key, RuntimeProviderModelStatus.ACTIVE,
                context, null, structured, false, null, null, null,
                priority, weight, "{}", capabilities);
    }

    private RuntimeProviderEndpoint endpoint(long id, RuntimeProviderStatus status, int priority, int weight) {
        return new RuntimeProviderEndpoint(id, "default", "https://example.com", null,
                priority, weight, status, 1000, 60000, null, "{}");
    }

    private record FakeRepository(List<RuntimeProviderDefinition> values)
            implements RuntimeProviderDefinitionRepository {
        @Override public List<RuntimeProviderDefinition> findRoutable() { return values; }
        @Override public Optional<RuntimeProviderDefinition> findRoutableByKey(String providerKey) {
            return values.stream().filter(value -> value.key().equals(providerKey)).findFirst();
        }
    }
}
