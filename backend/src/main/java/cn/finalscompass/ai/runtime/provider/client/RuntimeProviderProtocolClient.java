package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;

/**
 * 运行时供应商Protocol客户端的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里。
 */
public interface RuntimeProviderProtocolClient {
  String adapterKey();

  RuntimeProviderClientResult invoke(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput);

  default RuntimeProviderClientResult continueWithToolResults(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeProviderContinuation continuation) {
    throw new IllegalStateException("Runtime Provider client does not support Tool continuation");
  }
}
