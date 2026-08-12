package cn.finalscompass.ai.runtime.trace;

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
