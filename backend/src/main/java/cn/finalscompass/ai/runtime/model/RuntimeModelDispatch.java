package cn.finalscompass.ai.runtime.model;

import java.util.List;

/**
 * 运行时模型Dispatch的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：统一模型命令、回退和执行结果契约变化时修改这里。
 */
public record RuntimeModelDispatch(
    String executorType,
    RuntimeModelInvocationCommand primary,
    List<RuntimeModelInvocationCommand> fallbacks) {
  public RuntimeModelDispatch {
    fallbacks = List.copyOf(fallbacks);
  }
}
