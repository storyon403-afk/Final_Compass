package cn.finalscompass.ai.runtime.mcp;

/**
 * 表示运行时MCPProtocolException场景下可识别并向上层传播的失败。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public final class RuntimeMcpProtocolException extends RuntimeException {
  private final String errorCode;
  private final boolean retryable;

  public RuntimeMcpProtocolException(String errorCode, boolean retryable, Throwable cause) {
    super("Runtime MCP protocol failed: " + errorCode, cause);
    this.errorCode = errorCode;
    this.retryable = retryable;
  }

  public String errorCode() {
    return errorCode;
  }

  public boolean retryable() {
    return retryable;
  }
}
