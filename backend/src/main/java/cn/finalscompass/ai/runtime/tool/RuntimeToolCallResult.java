package cn.finalscompass.ai.runtime.tool;

public record RuntimeToolCallResult(
    String callId, String toolKey, boolean success, String outputJson, String errorCode) {}
