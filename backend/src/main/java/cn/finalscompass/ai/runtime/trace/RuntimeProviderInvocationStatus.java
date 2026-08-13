package cn.finalscompass.ai.runtime.trace;

/**
 * 定义运行时供应商调用状态允许使用的固定取值。
 * 维护入口：执行链路、状态和审计字段变化时修改这里。
 */
public enum RuntimeProviderInvocationStatus {
  ACCEPTED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  TIMEOUT,
  CANCELLED;

  public boolean terminal() {
    return this == SUCCEEDED || this == FAILED || this == TIMEOUT || this == CANCELLED;
  }
}
