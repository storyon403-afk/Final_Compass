package cn.finalscompass.ai.runtime.mcp;

public interface RuntimeMcpDiscoveryStore {
  RuntimeMcpDiscoveryPersistResult saveCurrent(RuntimeMcpDiscoverySnapshot snapshot);
}
