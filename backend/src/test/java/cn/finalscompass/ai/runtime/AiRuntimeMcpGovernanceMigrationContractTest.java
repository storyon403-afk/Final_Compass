package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AiRuntimeMcpGovernanceMigrationContractTest {
    @Test
    void definesPkceTokenStorageApprovalAndRestrictedStdioConfiguration() throws Exception {
        String migration;
        try (var stream=getClass().getResourceAsStream("/db/migration/V34__ai_runtime_mcp_governance.sql")) {
            assertNotNull(stream); migration=new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(migration.contains("CREATE TABLE ai_runtime_mcp_oauth_connection"));
        assertTrue(migration.contains("encrypted_access_token"));
        assertTrue(migration.contains("encrypted_refresh_token"));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_mcp_approval"));
        assertTrue(migration.contains("stdio_command JSON"));
        assertTrue(migration.contains("'PENDING','APPROVED','REJECTED'"));
        assertFalse(migration.contains("client_secret"));
    }
}
