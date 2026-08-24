package cn.finalscompass.ai.runtime.mcp;

import java.util.List;

/**
 * 运行时MCP发现快照的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里
 */
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
