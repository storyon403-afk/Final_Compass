package cn.finalscompass.ai.runtime.mcp;

public interface RuntimeMcpCredentialResolver {
  RuntimeMcpAuthMode authMode();

  RuntimeMcpCredential resolve(RuntimeMcpServerDefinition server, long userId);
}
