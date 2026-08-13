package cn.finalscompass.ai.runtime.mcp;

/**
 * 运行时MCP调用请求的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public record RuntimeMcpCallRequest(
    RuntimeMcpServerDefinition server,
    String remoteToolName,
    String argumentsJson,
    long executionId,
    long nodeId,
    long userId) {}
