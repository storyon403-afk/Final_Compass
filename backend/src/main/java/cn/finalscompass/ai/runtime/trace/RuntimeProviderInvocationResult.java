package cn.finalscompass.ai.runtime.trace;

import java.math.BigDecimal;

public record RuntimeProviderInvocationResult(
    long inputUnits,
    long outputUnits,
    BigDecimal estimatedCost,
    String currency,
    Long latencyMs,
    String providerRequestId,
    String errorCode,
    String errorSummary,
    String metadataJson) {}
