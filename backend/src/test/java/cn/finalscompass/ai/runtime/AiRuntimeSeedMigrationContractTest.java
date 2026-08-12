package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRuntimeSeedMigrationContractTest {
    private static final Set<String> SKILLS = Set.of(
            "math-problem-image-analysis", "progressive-hint", "complete-solution", "solution-review",
            "concept-explanation", "course-question-answering", "material-summary",
            "statistics-method-selector", "exam-focus-analysis", "study-plan-generation",
            "learning-result-synthesis");

    @Test
    void providerSeedIsConservativeAndContainsNoCredentialsOrAgentRuntime() throws IOException {
        String migration = migration("V28__seed_ai_runtime_providers.sql");
        assertTrue(migration.contains("'TEXT_REASONING'"));
        assertTrue(migration.contains("'VISION'"));
        assertTrue(migration.contains("'openai','OpenAI','API','openai-responses-v1'"));
        assertTrue(migration.contains("'deepseek','DeepSeek','API','deepseek-chat-v1'"));
        assertTrue(migration.contains("'gemini','Google / Gemini','API','gemini-generate-content-v1'"));
        assertTrue(migration.contains("FROM platform_ai_config"));
        assertFalse(migration.contains("'hermes','"));
        assertFalse(migration.contains("encrypted_key"));
        assertFalse(migration.contains("encryption_iv"));
    }

    @Test
    void skillSeedContainsEveryLegacySkillAndPublishesVersionedSnapshots() throws IOException {
        String migration = migration("V29__seed_ai_runtime_skills.sql");
        SKILLS.forEach(skill -> assertTrue(migration.contains("'" + skill + "'"), skill));
        assertTrue(migration.contains("'PERCEPTION'"));
        assertTrue(migration.contains("'REASONING'"));
        assertTrue(migration.contains("'PLANNING'"));
        assertTrue(migration.contains("'GENERATION'"));
        assertTrue(migration.contains("'1.0.0','PUBLISHED','LLM_PROMPT','provider-prompt-v1'"));
        assertTrue(migration.contains("'templateFormat','LEGACY_V2'"));
        assertTrue(migration.contains("'legacyCategory',d.legacy_category,'modalities',d.modalities"));
        assertTrue(migration.contains("('solution-review','LEARNING',JSON_ARRAY('TEXT','IMAGE')"));
        assertTrue(migration.contains("只有工具实际返回的已审核资料才能作为校内课程依据"));
        assertTrue(migration.contains("\\\\int_0^1 f(x)dx"));
        assertTrue(migration.contains("SET s.current_version_id=v.id"));
    }

    private String migration(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(stream, name + " must be packaged");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
