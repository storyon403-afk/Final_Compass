package cn.finalscompass.ai.runtime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class AiRuntimeExecutionTraceMigrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("finals")
            .withUsername("finals")
            .withPassword("finals-test");

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void createsExecutionTraceTables() throws SQLException {
        try (Connection connection = MYSQL.createConnection("");
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_name IN (
                       'ai_runtime_execution','ai_runtime_execution_node',
                       'ai_runtime_provider_invocation','ai_runtime_execution_event')
                     """)) {
            result.next();
            assertEquals(4, result.getInt(1));
        }
    }

    @Test
    void rejectsDuplicateTraceAndFailedExecutionWithoutErrorCode() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            insertExecution(connection, "exec-trace-one", "trace-one", "CREATED", null, null);
            assertThrows(SQLException.class,
                    () -> insertExecution(connection, "exec-trace-two", "trace-one", "CREATED", null, null));
            assertThrows(SQLException.class, () -> insertExecution(connection, "exec-failed", "trace-failed",
                    "FAILED", null, Timestamp.from(Instant.now())));
        }
    }

    @Test
    void nodeSkillVersionMustBelongToTheRecordedSkill() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            long firstSkill = insertSkill(connection, "trace-first-skill");
            long secondSkill = insertSkill(connection, "trace-second-skill");
            long version = insertSkillVersion(connection, firstSkill);
            long execution = insertExecution(connection, "exec-node-skill", "trace-node-skill", "CREATED", null, null);

            try (var statement = connection.prepareStatement("""
                    INSERT INTO ai_runtime_execution_node(
                      execution_id,node_key,node_type,skill_id,skill_version_id,
                      skill_key_snapshot,skill_version_snapshot,metadata)
                    VALUES (?,'reasoning','SKILL',?,?, 'trace-second-skill','1.0.0',JSON_OBJECT())
                    """)) {
                statement.setLong(1, execution);
                statement.setLong(2, secondSkill);
                statement.setLong(3, version);
                assertThrows(SQLException.class, statement::executeUpdate);
            }
        }
    }

    @Test
    void eventNodeMustBelongToTheSameExecutionAndSequenceIsUnique() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            long firstExecution = insertExecution(connection, "exec-event-one", "trace-event-one", "CREATED", null, null);
            long secondExecution = insertExecution(connection, "exec-event-two", "trace-event-two", "CREATED", null, null);
            long node = insertNode(connection, firstExecution, "event-node");
            insertEvent(connection, firstExecution, node, 1);
            assertThrows(SQLException.class, () -> insertEvent(connection, firstExecution, node, 1));
            assertThrows(SQLException.class, () -> insertEvent(connection, secondExecution, node, 1));
        }
    }

    private static long insertExecution(Connection connection, String executionId, String traceId,
                                        String status, String errorCode, Timestamp completedAt) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_execution(
                  execution_id,trace_id,user_id,runtime_type,status,error_code,completed_at,metadata)
                VALUES (?,?,1,'LEGACY',?,?,?,JSON_OBJECT())
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, executionId);
            statement.setString(2, traceId);
            statement.setString(3, status);
            statement.setString(4, errorCode);
            statement.setTimestamp(5, completedAt);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    private static long insertSkill(Connection connection, String key) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_skill(skill_key,name,skill_type,description,domain_tags)
                VALUES (?,?,'REASONING','trace test',JSON_ARRAY())
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, key); statement.setString(2, key); statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    private static long insertSkillVersion(Connection connection, long skillId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_skill_version(
                  skill_id,version,lifecycle_status,executor_type,executor_key,input_schema,output_schema,
                  required_capabilities,permission_policy,allowed_tools,configuration,max_input_units,
                  timeout_ms,retry_policy)
                VALUES (?,'1.0.0','DRAFT','LLM_PROMPT','provider-prompt-v1',JSON_OBJECT(),JSON_OBJECT(),
                  JSON_ARRAY(),JSON_OBJECT(),JSON_ARRAY(),JSON_OBJECT(),8000,60000,JSON_OBJECT())
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, skillId); statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    private static long insertNode(Connection connection, long executionId, String key) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_execution_node(execution_id,node_key,node_type,metadata)
                VALUES (?,?,'MODEL',JSON_OBJECT())
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, executionId); statement.setString(2, key); statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
        }
    }

    private static void insertEvent(Connection connection, long executionId, long nodeId, long sequence)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_execution_event(
                  execution_id,execution_node_id,sequence_no,event_type,event_payload)
                VALUES (?,?,?,'NODE_READY',JSON_OBJECT())
                """)) {
            statement.setLong(1, executionId); statement.setLong(2, nodeId);
            statement.setLong(3, sequence); statement.executeUpdate();
        }
    }
}
