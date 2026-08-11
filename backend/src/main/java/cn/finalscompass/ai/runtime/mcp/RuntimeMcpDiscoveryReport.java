package cn.finalscompass.ai.runtime.mcp;

public record RuntimeMcpDiscoveryReport(
        String discoveryId, long snapshotId, String serverKey,
        String schemaDigest, int toolCount, int staleBindings
) {}
