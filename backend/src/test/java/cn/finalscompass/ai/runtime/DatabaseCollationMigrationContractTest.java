package cn.finalscompass.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DatabaseCollationMigrationContractTest {
    @Test
    void normalizesEveryLegacyTableThatDidNotDeclareTheProjectCollation() throws IOException {
        String migration = migration("V61__normalize_database_collation.sql");
        assertTrue(migration.contains("ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"));
        assertEquals(18, migration.split("CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci", -1).length - 1);
        assertTrue(migration.contains("ALTER TABLE ai_runtime_run "));
        assertTrue(migration.contains("ALTER TABLE ai_web_agent_participant "));
        assertTrue(migration.contains("ALTER TABLE user_ai_vision_secret "));
    }

    private String migration(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(stream, name + " must be packaged");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
