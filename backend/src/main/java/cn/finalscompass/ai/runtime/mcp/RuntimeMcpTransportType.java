package cn.finalscompass.ai.runtime.mcp;

/**
 * 定义运行时MCP传输类型允许使用的固定取值。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public enum RuntimeMcpTransportType {
  STREAMABLE_HTTP,
  STDIO
}
