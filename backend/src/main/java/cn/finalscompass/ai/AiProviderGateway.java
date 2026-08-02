package cn.finalscompass.ai;

public interface AiProviderGateway {
    AiProviderResult invoke(AiProviderRequest request, char[] apiKey);

    record AiProviderRequest(String provider, String model, AiSkill skill, String input) {}
    record AiProviderResult(String content, int inputUnits, int outputUnits) {}
}
