package cn.finalscompass.ai.runtime.mcp;

import java.util.List;

/**
 * 运行时MCP发现结果的数据载体，用于在相邻运行时组件之间传递不可变数据
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里
 */
public record RuntimeMcpDiscoveryResult(
    String protocolVersion, String serverCapabilitiesJson, List<RuntimeMcpDiscoveredTool> tools) {
  public RuntimeMcpDiscoveryResult {
    tools = List.copyOf(tools);
  }
}
