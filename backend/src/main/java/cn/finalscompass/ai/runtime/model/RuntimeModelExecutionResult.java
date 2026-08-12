package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.tool.RuntimeToolCall;
import java.util.List;

public record RuntimeModelExecutionResult(
    String content,
    int inputUnits,
    int outputUnits,
    boolean preview,
    String providerKey,
    String modelKey,
    long providerInvocationId,
    List<RuntimeToolCall> toolCalls) {
  public RuntimeModelExecutionResult {
    toolCalls = List.copyOf(toolCalls);
  }
}
