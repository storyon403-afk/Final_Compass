package cn.finalscompass.ai.runtime.trace;

/**
 * 创建参数运行时执行节点的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：执行链路、状态和审计字段变化时修改这里
 */
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
