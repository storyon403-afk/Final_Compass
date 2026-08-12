package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRuntimeMcpRegistryMigrationContractTest {
    @Test
    void definesServersDiscoverySnapshotsAndPinnedToolBindingsWithoutSecretColumns() throws Exception {
        String migration;
        try (var stream = getClass().getResourceAsStream("/db/migration/V33__ai_runtime_mcp_registry.sql")) {
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(migration.contains("CREATE TABLE ai_runtime_mcp_server ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_mcp_discovery_snapshot ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_mcp_discovered_tool ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_mcp_tool_binding ("));
        assertTrue(migration.contains("'STREAMABLE_HTTP','STDIO'"));
        assertTrue(migration.contains("credential_reference"));
        assertTrue(migration.contains("pinned_schema_digest"));
        assertTrue(migration.contains("'ACTIVE','DISABLED','STALE'"));
        assertTrue(!migration.contains("access_token") && !migration.contains("client_secret"));
    }
}
