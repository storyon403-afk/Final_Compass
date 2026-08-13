package cn.finalscompass.ai.runtime.mcp;

/**
 * 定义运行时MCP认证模式允许使用的固定取值。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public enum RuntimeMcpAuthMode {
  NONE,
  PLATFORM_OAUTH,
  USER_OAUTH,
  SERVICE_TOKEN
}
