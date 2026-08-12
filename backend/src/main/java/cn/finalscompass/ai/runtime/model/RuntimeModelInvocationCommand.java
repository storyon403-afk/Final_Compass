package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;
import cn.finalscompass.ai.runtime.tool.RuntimeToolSpecification;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record RuntimeModelInvocationCommand(
    long providerId,
    String providerKey,
    RuntimeProviderType providerType,
    String adapterKey,
    long providerModelId,
    String modelKey,
    long endpointId,
    String endpointKey,
    String baseUrl,
    String credentialSource,
    String skillKey,
    String skillVersion,
    String systemInstruction,
    String userInput,
    String contextJson,
    String outputContract,
    String outputSchemaJson,
    Set<String> allowedTools,
    List<RuntimeToolSpecification> toolSpecifications,
    Set<String> modalities,
    boolean structuredOutputRequired,
    BigDecimal inputUnitPrice,
    BigDecimal outputUnitPrice,
    String pricingCurrency,
    int connectTimeoutMs,
    int timeoutMs) {
  public RuntimeModelInvocationCommand {
    allowedTools = Set.copyOf(allowedTools);
    toolSpecifications = List.copyOf(toolSpecifications);
    modalities = Set.copyOf(modalities);
  }
}
