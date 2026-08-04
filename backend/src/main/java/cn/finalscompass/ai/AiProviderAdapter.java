package cn.finalscompass.ai;

import java.util.Set;

/** Isolates one external AI provider's request and response conventions. */
public interface AiProviderAdapter {
    String id();
    String displayName();
    Set<String> capabilities();
    AiProviderResult invoke(AiProviderRequest request, char[] apiKey);

    record AiProviderRequest(String model, AiSkillPlanner.ExecutionPlan plan, TransientImage image) {}
    record TransientImage(String mediaType, byte[] bytes) implements AutoCloseable {
        @Override public void close() { if (bytes != null) java.util.Arrays.fill(bytes, (byte) 0); }
    }
    record AiProviderResult(String content, int inputUnits, int outputUnits, boolean preview) {}
}
