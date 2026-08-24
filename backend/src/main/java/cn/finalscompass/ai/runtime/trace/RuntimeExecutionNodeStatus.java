package cn.finalscompass.ai.runtime.trace;

/**
 * 定义运行时执行节点状态允许使用的固定取值
 * 维护入口：执行链路、状态和审计字段变化时修改这里
 */
public enum RuntimeExecutionNodeStatus {
  PENDING,
  READY,
  RUNNING,
  WAITING_USER,
  WAITING_TOOL,
  RETRYING,
  SUCCEEDED,
  FAILED,
  SKIPPED,
  CANCELLED;

  public boolean terminal() {
    return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELLED;
  }
}
