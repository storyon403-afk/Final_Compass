package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.runtime.tool.RuntimeToolCall;
import java.util.List;

public record RuntimeProviderClientResult(
    String content,
    int inputUnits,
    int outputUnits,
    boolean preview,
    String providerRequestId,
    List<RuntimeToolCall> toolCalls,
    String continuationState) {
  public RuntimeProviderClientResult {
    toolCalls = List.copyOf(toolCalls);
  }
}
