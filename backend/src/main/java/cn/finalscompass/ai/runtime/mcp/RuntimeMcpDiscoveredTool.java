package cn.finalscompass.ai.runtime.mcp;

public record RuntimeMcpDiscoveredTool(
        String name, String title, String description,
        String inputSchemaJson, String outputSchemaJson, String annotationsJson
) {}
