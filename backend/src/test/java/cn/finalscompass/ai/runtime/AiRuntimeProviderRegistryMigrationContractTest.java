package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRuntimeProviderRegistryMigrationContractTest {
    @Test
    void migrationSeparatesProvidersModelsEndpointsAndCapabilities() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V26__ai_runtime_provider_registry.sql")) {
            assertNotNull(stream, "V26 Provider Registry migration must be packaged");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("CREATE TABLE ai_runtime_provider ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_provider_endpoint ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_provider_model ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_capability ("));
        assertTrue(migration.contains("CREATE TABLE ai_runtime_provider_model_capability ("));
        assertTrue(migration.contains("provider_type IN ('API','BROWSER','LOCAL')"));
        assertTrue(migration.contains("adapter_key = 'browser-agent-gateway-v1'"));
        assertTrue(migration.contains("PRIMARY KEY (provider_model_id, capability_id)"));
        assertFalse(migration.contains("encrypted_key"));
        assertFalse(migration.contains("encryption_iv"));
        assertFalse(migration.contains("class_name"));
    }
}
