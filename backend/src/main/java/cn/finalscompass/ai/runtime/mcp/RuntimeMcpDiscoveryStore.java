package cn.finalscompass.ai.runtime.mcp;

/**
 * 运行时MCP发现存储的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public interface RuntimeMcpDiscoveryStore {
  RuntimeMcpDiscoveryPersistResult saveCurrent(RuntimeMcpDiscoverySnapshot snapshot);
}
