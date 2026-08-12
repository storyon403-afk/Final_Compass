package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AiKnowledgeEmbeddingMigrationContractTest {
    @Test void registersEmbeddingCapabilityModelJobsAndChunkState() throws Exception {
        String sql;try(var stream=getClass().getResourceAsStream("/db/migration/V36__knowledge_embedding_pipeline.sql")){assertNotNull(stream);sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
        assertTrue(sql.contains("embedding_status"));assertTrue(sql.contains("knowledge_embedding_job"));
        assertTrue(sql.contains("embeddingAdapterKey"));assertTrue(sql.contains("'EMBEDDING'"));
        assertTrue(sql.contains("text-embedding-3-small"));assertFalse(sql.contains("api_key"));assertFalse(sql.contains("client_secret"));
    }
}
