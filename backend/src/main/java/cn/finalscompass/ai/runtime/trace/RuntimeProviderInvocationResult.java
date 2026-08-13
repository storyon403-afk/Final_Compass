package cn.finalscompass.ai.runtime.trace;

import java.math.BigDecimal;

/**
 * 运行时供应商调用结果的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：执行链路、状态和审计字段变化时修改这里。
 */
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
