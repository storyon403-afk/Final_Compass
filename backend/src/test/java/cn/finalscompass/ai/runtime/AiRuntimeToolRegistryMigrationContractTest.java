package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRuntimeToolRegistryMigrationContractTest {
    @Test
    void definesVersionedToolsWithPermissionsSchemasAndBoundedExecution() throws Exception {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V32__ai_runtime_tool_registry.sql")) {
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(migration.contains("CREATE TABLE ai_runtime_tool ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_tool_version ("));
        assertTrue(migration.contains("'INTERNAL','MCP','HTTP','BROWSER'"));
        assertTrue(migration.contains("permission_policy JSON NOT NULL"));
        assertTrue(migration.contains("max_result_bytes"));
        assertTrue(migration.contains("fk_ai_runtime_tool_current_version"));
    }
}
