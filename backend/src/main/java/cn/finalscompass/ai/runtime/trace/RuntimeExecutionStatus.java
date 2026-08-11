package cn.finalscompass.ai.runtime.trace;

public enum RuntimeExecutionStatus {
    CREATED, PLANNING, RUNNING, WAITING_USER, WAITING_TOOL, RETRYING,
    SUCCEEDED, FAILED, CANCELLED;

    public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
}
