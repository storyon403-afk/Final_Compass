package cn.finalscompass.ai.runtime.mcp;

public record RuntimeMcpServerDefinition(
        long id, String serverKey, String name, RuntimeMcpTransportType transportType,
        String endpointUri, String protocolVersion, RuntimeMcpAuthMode authMode,
        String credentialReference, RuntimeMcpHealthStatus healthStatus,
        String outboundPolicyJson, String configurationJson
) {}
