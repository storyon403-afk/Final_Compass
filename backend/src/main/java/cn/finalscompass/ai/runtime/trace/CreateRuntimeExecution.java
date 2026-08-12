package cn.finalscompass.ai.runtime.trace;

public record CreateRuntimeExecution(
    String executionId,
    String traceId,
    Long parentExecutionId,
    Long legacyTaskId,
    long userId,
    String sessionId,
    RuntimeType runtimeType,
    String goalSummary,
    String inputReference,
    String workflowKey,
    String workflowVersion,
    String metadataJson) {}
