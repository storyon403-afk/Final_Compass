package cn.finalscompass.ai.runtime.tool;

import java.util.Set;

public record RuntimeToolDefinition(
        long id, String toolKey, String name, String description, String version,
        RuntimeToolTransportType transportType, String executorKey,
        String inputSchemaJson, String outputSchemaJson, Set<String> requiredPermissions,
        String configurationJson, int timeoutMs, int maxResultBytes
) {
    public RuntimeToolDefinition { requiredPermissions = Set.copyOf(requiredPermissions); }
}
