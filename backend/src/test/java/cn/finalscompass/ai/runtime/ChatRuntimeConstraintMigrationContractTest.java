package cn.finalscompass.ai.runtime;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ChatRuntimeConstraintMigrationContractTest {
    @Test void executionConstraintIncludesEveryRuntimeEnumValue() throws Exception {
        String sql;
        try (var stream=getClass().getResourceAsStream("/db/migration/V54__allow_chat_runtime_execution.sql")) {
            assertNotNull(stream);
            sql=new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("DROP CHECK chk_ai_runtime_execution_runtime"));
        for (var runtime : cn.finalscompass.ai.runtime.trace.RuntimeType.values())
            assertTrue(sql.contains("'"+runtime.name()+"'"), runtime+" must be allowed by the database constraint");
    }
}
