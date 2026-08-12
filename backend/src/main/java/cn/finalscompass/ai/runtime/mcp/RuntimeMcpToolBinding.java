package cn.finalscompass.ai.runtime.mcp;

public record RuntimeMcpToolBinding(
    String toolKey,
    String toolVersion,
    RuntimeMcpServerDefinition server,
    String remoteToolName,
    String pinnedSchemaDigest) {}
