package cn.finalscompass.ai.runtime.mcp;

import java.util.List;

public record RuntimeMcpDiscoveryResult(
    String protocolVersion, String serverCapabilitiesJson, List<RuntimeMcpDiscoveredTool> tools) {
  public RuntimeMcpDiscoveryResult {
    tools = List.copyOf(tools);
  }
}
