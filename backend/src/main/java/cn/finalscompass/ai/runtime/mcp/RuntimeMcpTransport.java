package cn.finalscompass.ai.runtime.mcp;

public interface RuntimeMcpTransport {
  RuntimeMcpTransportType transportType();

  RuntimeMcpCallResult callTool(RuntimeMcpCallRequest request, char[] accessToken);

  default RuntimeMcpDiscoveryResult discoverTools(
      RuntimeMcpServerDefinition server, char[] accessToken) {
    throw new IllegalStateException("Runtime MCP transport does not support Tool discovery");
  }
}
