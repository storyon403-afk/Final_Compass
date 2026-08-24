package cn.finalscompass.ai.runtime.trace;

/**
 * 运行时执行追踪存储的抽象契约，用于隔离业务编排与具体实现
 * 维护入口：执行链路、状态和审计字段变化时修改这里
 */
public interface RuntimeExecutionTraceStore {
  long createExecution(CreateRuntimeExecution command);

  long createNode(CreateRuntimeExecutionNode command);

  long createProviderInvocation(CreateRuntimeProviderInvocation command);

  void transitionExecution(
      long executionId,
      RuntimeExecutionStatus target,
      String resultReference,
      String errorCode,
      String errorSummary);

  void transitionNode(
      long nodeId,
      RuntimeExecutionNodeStatus target,
      String outputReference,
      String outputDigest,
      String errorCode,
      String errorSummary);

  void transitionProviderInvocation(
      long invocationId,
      RuntimeProviderInvocationStatus target,
      RuntimeProviderInvocationResult result);

  long appendEvent(long executionId, Long nodeId, String eventType, String payloadJson);
}
