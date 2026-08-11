package cn.finalscompass.ai.runtime.trace;

public interface RuntimeExecutionTraceStore {
    long createExecution(CreateRuntimeExecution command);
    long createNode(CreateRuntimeExecutionNode command);
    long createProviderInvocation(CreateRuntimeProviderInvocation command);
    void transitionExecution(long executionId, RuntimeExecutionStatus target, String resultReference,
                             String errorCode, String errorSummary);
    void transitionNode(long nodeId, RuntimeExecutionNodeStatus target, String outputReference,
                        String outputDigest, String errorCode, String errorSummary);
    void transitionProviderInvocation(long invocationId, RuntimeProviderInvocationStatus target,
                                      RuntimeProviderInvocationResult result);
    long appendEvent(long executionId, Long nodeId, String eventType, String payloadJson);
}
