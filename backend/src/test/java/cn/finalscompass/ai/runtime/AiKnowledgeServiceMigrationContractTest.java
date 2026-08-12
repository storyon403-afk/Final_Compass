package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AiKnowledgeServiceMigrationContractTest {
    @Test void createsIsolatedKnowledgeTablesAndPermissionedTool() throws Exception {
        String sql;try(var stream=getClass().getResourceAsStream("/db/migration/V35__knowledge_service_foundation.sql")){assertNotNull(stream);sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
        assertTrue(sql.contains("CREATE TABLE knowledge_source"));assertTrue(sql.contains("CREATE TABLE knowledge_document"));assertTrue(sql.contains("CREATE TABLE knowledge_chunk"));
        assertTrue(sql.contains("FULLTEXT INDEX"));assertTrue(sql.contains("embedding BLOB"));assertTrue(sql.contains("'Knowledge.search'"));assertTrue(sql.contains("'KNOWLEDGE_READ'"));
        assertFalse(sql.contains("REFERENCES resource"));assertFalse(sql.contains("REFERENCES course"));assertFalse(sql.contains("REFERENCES app_user"));
    }
}
