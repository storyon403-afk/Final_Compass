package cn.finalscompass.ai.runtime.mcp;

/**
 * 运行时MCP传输的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public interface RuntimeMcpTransport {
  RuntimeMcpTransportType transportType();

  RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] accessToken);

  default RuntimeMcpDiscoveryResult discoverTools(
      RuntimeMcpServerDefinition server, char[] accessToken) {
    throw new IllegalStateException("Runtime MCP transport does not support Tool discovery");
  }
}
