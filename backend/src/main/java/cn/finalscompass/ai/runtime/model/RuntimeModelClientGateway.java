package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.provider.client.RuntimeBinaryInput;
import cn.finalscompass.ai.runtime.tool.RuntimeToolExecutionContext;

/**
 * 运行时模型客户端网关的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：统一模型命令、回退和执行结果契约变化时修改这里。
 */
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
