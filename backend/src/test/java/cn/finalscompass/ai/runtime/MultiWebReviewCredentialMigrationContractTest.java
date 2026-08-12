package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MultiWebReviewCredentialMigrationContractTest {
    @Test void createsIndependentPlatformAndUserReviewCredentials() throws Exception {
        String sql;
        try (var stream=getClass().getResourceAsStream("/db/migration/V53__multiweb_review_credential.sql")) {
            assertNotNull(stream);
            sql=new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("CREATE TABLE platform_ai_review_config"));
        assertTrue(sql.contains("CREATE TABLE user_ai_review_secret"));
        assertTrue(sql.contains("PRIMARY KEY(user_id,provider)"));
        assertFalse(sql.toLowerCase().contains("api_key varchar"));
    }
}
