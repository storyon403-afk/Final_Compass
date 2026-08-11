package cn.finalscompass.ai.runtime.mcp;

public record RuntimeMcpCallRequest(
        RuntimeMcpServerDefinition server, String remoteToolName, String argumentsJson,
        long executionId, long nodeId, long userId
) {}
