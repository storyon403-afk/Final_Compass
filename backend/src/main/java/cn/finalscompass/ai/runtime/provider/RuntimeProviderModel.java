package cn.finalscompass.ai.runtime.provider;

import java.math.BigDecimal;
import java.util.Set;

public record RuntimeProviderModel(
    long id,
    String key,
    String displayName,
    RuntimeProviderModelStatus status,
    Integer contextWindow,
    Integer maxOutputUnits,
    boolean structuredOutput,
    boolean toolCalling,
    BigDecimal inputUnitPrice,
    BigDecimal outputUnitPrice,
    String currency,
    int routingPriority,
    int routingWeight,
    String configurationJson,
    Set<String> capabilities) {
  public RuntimeProviderModel {
    capabilities = Set.copyOf(capabilities);
  }
}
