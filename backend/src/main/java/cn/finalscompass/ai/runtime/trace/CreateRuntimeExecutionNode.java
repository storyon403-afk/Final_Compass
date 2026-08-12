package cn.finalscompass.ai.runtime.trace;

public record CreateRuntimeExecutionNode(
    long executionId,
    Long parentNodeId,
    String nodeKey,
    RuntimeExecutionNodeType nodeType,
    Long skillId,
    Long skillVersionId,
    String skillKeySnapshot,
    String skillVersionSnapshot,
    int attempt,
    String inputReference,
    String inputDigest,
    String metadataJson) {}
