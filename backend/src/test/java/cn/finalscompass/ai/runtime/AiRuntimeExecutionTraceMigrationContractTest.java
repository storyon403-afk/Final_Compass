package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRuntimeExecutionTraceMigrationContractTest {
    @Test
    void migrationPreservesVersionedTraceAndExcludesSensitiveContent() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V27__ai_runtime_execution_trace.sql")) {
            assertNotNull(stream, "V27 Execution Trace migration must be packaged");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("CREATE TABLE ai_runtime_execution ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_execution_node ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_provider_invocation ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_execution_event ("));
        assertTrue(migration.contains("'LEGACY','WORKFLOW','AGENT','MULTI_WEB_AGENT'"));
        assertTrue(migration.contains("FOREIGN KEY (skill_version_id, skill_id)"));
        assertTrue(migration.contains("FOREIGN KEY (provider_model_id, provider_id)"));
        assertTrue(migration.contains("FOREIGN KEY (execution_node_id, execution_id)"));
        assertTrue(migration.contains("UNIQUE (execution_id, sequence_no)"));
        assertFalse(migration.contains("input_text"));
        assertFalse(migration.contains("result_text"));
        assertFalse(migration.contains("encrypted_key"));
        assertFalse(migration.contains("api_key"));
    }
}
