package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AiFeedbackMigrationContractTest {
    @Test void createsTraceLinkedFeedbackSnapshotsAndOptimizationQueue()throws Exception{
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V39__ai_feedback_optimization.sql"));
        assertTrue(sql.contains("CREATE TABLE ai_feedback_prompt"));
        assertTrue(sql.contains("CREATE TABLE ai_task_feedback"));
        assertTrue(sql.contains("CREATE TABLE ai_feedback_skill_snapshot"));
        assertTrue(sql.contains("CREATE TABLE ai_feedback_provider_snapshot"));
        assertTrue(sql.contains("CREATE TABLE ai_feedback_tool_snapshot"));
        assertTrue(sql.contains("CREATE TABLE ai_pending_skill_optimization"));
        assertTrue(sql.contains("UNIQUE(feedback_id,skill_version_id)"));
        assertTrue(sql.contains("'UNDERSTANDING_ERROR','CONTENT_ERROR','INCOMPLETE','POOR_REASONING','FORMAT_LAYOUT','STYLE_MISMATCH','SLOW_RESPONSE','OTHER'"));
    }
}
