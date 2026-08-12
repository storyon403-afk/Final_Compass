package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;

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
