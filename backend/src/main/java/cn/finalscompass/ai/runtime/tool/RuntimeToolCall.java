package cn.finalscompass.ai.runtime.tool;

public record RuntimeToolCall(String callId, String toolKey, String argumentsJson) {
    public RuntimeToolCall {
        if (callId == null || !callId.matches("[A-Za-z0-9_-]{1,160}"))
            throw new IllegalArgumentException("Runtime Tool call id is invalid");
        if (toolKey == null || !toolKey.matches("[A-Za-z][A-Za-z0-9]*(?:[._-][A-Za-z0-9]+)*"))
            throw new IllegalArgumentException("Runtime Tool key is invalid");
        if (argumentsJson == null || argumentsJson.length() > 65536)
            throw new IllegalArgumentException("Runtime Tool arguments are invalid");
    }
}
