package cn.finalscompass.ai.runtime.model;

import cn.finalscompass.ai.runtime.tool.RuntimeToolCall;
import java.util.List;

/**
 * 运行时模型执行结果的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：统一模型命令、回退和执行结果契约变化时修改这里。
 */
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
