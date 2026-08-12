package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.provider.client.RuntimeBinaryInput;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutionContext;

public interface RuntimeModelClientGateway {
  default RuntimeModelExecutionResult execute(
      long executionNodeId,
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials) {
    return execute(executionNodeId, dispatch, credentials, null);
  }

  RuntimeModelExecutionResult execute(
      long executionNodeId,
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials,
      RuntimeBinaryInput binaryInput);

  default RuntimeModelExecutionResult execute(
      long executionNodeId,
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials,
      RuntimeBinaryInput binaryInput,
      int attemptOffset) {
    return execute(executionNodeId, dispatch, credentials, binaryInput);
  }

  RuntimeModelExecutionResult executeWithTools(
      RuntimeModelDispatch dispatch,
      RuntimeCredentialLeaseResolver credentials,
      RuntimeBinaryInput binaryInput,
      RuntimeToolExecutionContext toolContext,
      int maxToolRounds);
}
