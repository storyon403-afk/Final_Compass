package cn.finalscompass.ai.runtime.trace;

/**
 * 定义运行时执行状态允许使用的固定取值。
 * 维护入口：执行链路、状态和审计字段变化时修改这里。
 */
public enum RuntimeExecutionStatus {
  CREATED,
  PLANNING,
  RUNNING,
  WAITING_USER,
  WAITING_TOOL,
  RETRYING,
  SUCCEEDED,
  FAILED,
  CANCELLED;

  public boolean terminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED;
  }
}
