package cn.finalscompass.ai.runtime.mcp;

public record RuntimeMcpNormalizedTool(
    String name,
    String title,
    String description,
    String inputSchemaJson,
    String outputSchemaJson,
    String annotationsJson,
    String schemaDigest) {}
