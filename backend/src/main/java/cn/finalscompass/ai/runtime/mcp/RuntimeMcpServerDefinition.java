package cn.finalscompass.ai.runtime.mcp;

/**
 * 运行时MCP服务器定义的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public record RuntimeMcpServerDefinition(
    long id,
    String serverKey,
    String name,
    RuntimeMcpTransportType transportType,
    String endpointUri,
    String protocolVersion,
    RuntimeMcpAuthMode authMode,
    String credentialReference,
    RuntimeMcpHealthStatus healthStatus,
    String outboundPolicyJson,
    String configurationJson) {}
