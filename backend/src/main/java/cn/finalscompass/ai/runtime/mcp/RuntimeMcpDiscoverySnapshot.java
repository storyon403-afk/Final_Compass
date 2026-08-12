package cn.finalscompass.ai.runtime.mcp;

import java.util.List;

public record RuntimeMcpDiscoverySnapshot(
    String discoveryId,
    long serverId,
    String protocolVersion,
    String capabilitiesJson,
    String schemaDigest,
    List<RuntimeMcpNormalizedTool> tools) {
  public RuntimeMcpDiscoverySnapshot {
    tools = List.copyOf(tools);
  }
}
