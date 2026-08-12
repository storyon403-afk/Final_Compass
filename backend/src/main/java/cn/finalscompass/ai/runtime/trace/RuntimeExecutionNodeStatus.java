package cn.finalscompass.ai.runtime.trace;

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
