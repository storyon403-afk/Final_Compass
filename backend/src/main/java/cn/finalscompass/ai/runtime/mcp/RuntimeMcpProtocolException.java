package cn.finalscompass.ai.runtime.mcp;

public final class RuntimeMcpProtocolException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    public RuntimeMcpProtocolException(String errorCode, boolean retryable, Throwable cause) {
        super("Runtime MCP protocol failed: " + errorCode, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    public String errorCode() { return errorCode; }
    public boolean retryable() { return retryable; }
}
