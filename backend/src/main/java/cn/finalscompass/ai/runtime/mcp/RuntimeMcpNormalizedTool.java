package cn.finalscompass.ai.runtime.mcp;

/**
 * 运行时MCP标准化工具的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
public record RuntimeMcpNormalizedTool(
    String name,
    String title,
    String description,
    String inputSchemaJson,
    String outputSchemaJson,
    String annotationsJson,
    String schemaDigest) {}
