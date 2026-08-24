package cn.finalscompass.ai.runtime;
import org.junit.jupiter.api.Test;import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
class AiCenterContentMigrationContractTest{@Test void createsEditableUsageAndVcpPages()throws Exception{String sql=Files.readString(Path.of("src/main/resources/db/migration/V42__ai_center_content_pages.sql"));assertTrue(sql.contains("CREATE TABLE ai_center_content_page"));assertTrue(sql.contains("'USAGE_GUIDE'"));assertTrue(sql.contains("'VCP_INTRO'"));assertTrue(sql.contains("UNIQUE KEY uk_ai_center_content_page_key"));}}
