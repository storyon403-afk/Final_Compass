package cn.finalscompass.ai;

import java.util.Set;

/** Isolates one external AI provider's request and response conventions. */
public interface AiProviderAdapter {
    String id();
    String displayName();
    Set<String> capabilities();
    AiProviderResult invoke(AiProviderRequest request, char[] apiKey);

    record AiProviderRequest(String model, AiSkill skill, String input) {}
    record AiProviderResult(String content, int inputUnits, int outputUnits, boolean preview) {}
}
