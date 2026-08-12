package cn.finalscompass.ai.runtime.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRuntimeExecutionTraceStoreTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("finals").withUsername("finals").withPassword("finals-test");
    private static JdbcRuntimeExecutionTraceStore store;
    private static long providerId;
    private static long modelId;

    @BeforeAll
    static void migrateAndCreateStore() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (Connection connection = MYSQL.createConnection("");
             var providerQuery = connection.createStatement().executeQuery(
                     "SELECT id FROM ai_runtime_provider WHERE provider_key='deepseek'")) {
            providerQuery.next();
            providerId = providerQuery.getLong(1);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO ai_runtime_provider_model(
                      provider_id,model_key,display_name,status,routing_priority,routing_weight,configuration)
                    VALUES (?,'trace-test-model','Trace Test Model','ACTIVE',100,100,JSON_OBJECT())
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, providerId);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) { keys.next(); modelId = keys.getLong(1); }
            }
        }
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        store = new JdbcRuntimeExecutionTraceStore(JdbcClient.create(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper(), new RuntimeTraceStateMachine());
    }

    @Test
    void writesLifecycleAndGaplessOrderedEventsTransactionally() throws Exception {
        long executionId = store.createExecution(new CreateRuntimeExecution(
                "exec-lifecycle-1", "trace-lifecycle-1", null, null, 101, "session-1",
                RuntimeType.WORKFLOW, "test goal", "object://input/sha256", "exam-preparation", "1.0.0", "{}"));
        long nodeId = store.createNode(new CreateRuntimeExecutionNode(
                executionId, null, "understand", RuntimeExecutionNodeType.TASK_UNDERSTANDING,
                null, null, null, null, 1, "object://node/input", "a".repeat(64), "{}"));
        store.transitionExecution(executionId, RuntimeExecutionStatus.RUNNING, null, null, null);
        store.transitionNode(nodeId, RuntimeExecutionNodeStatus.READY, null, null, null, null);
        store.transitionNode(nodeId, RuntimeExecutionNodeStatus.RUNNING, null, null, null, null);
        store.transitionNode(nodeId, RuntimeExecutionNodeStatus.SUCCEEDED,
                "object://node/output", "b".repeat(64), null, null);
        assertEquals(7, store.appendEvent(executionId, nodeId, "QUALITY_CHECKED", "{\"passed\":true}"));
        store.transitionExecution(executionId, RuntimeExecutionStatus.SUCCEEDED,
                "object://result/final", null, null);

        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) total,MIN(sequence_no) first_sequence,MAX(sequence_no) last_sequence
                     FROM ai_runtime_execution_event WHERE execution_id=%d
                     """.formatted(executionId))) {
            result.next();
            assertEquals(8, result.getInt("total"));
            assertEquals(1, result.getLong("first_sequence"));
            assertEquals(8, result.getLong("last_sequence"));
        }
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT status,result_reference,next_event_sequence,completed_at IS NOT NULL completed
                     FROM ai_runtime_execution WHERE id=%d
                     """.formatted(executionId))) {
            result.next();
            assertEquals("SUCCEEDED", result.getString("status"));
            assertEquals("object://result/final", result.getString("result_reference"));
            assertEquals(9, result.getLong("next_event_sequence"));
            assertTrue(result.getBoolean("completed"));
        }
    }

    @Test
    void illegalTransitionRollsBackWithoutAppendingEvent() throws Exception {
        long executionId = store.createExecution(new CreateRuntimeExecution(
                "exec-invalid-1", "trace-invalid-1", null, null, 102, null,
                RuntimeType.AGENT, null, null, null, null, "{}"));
        assertThrows(IllegalStateException.class, () -> store.transitionExecution(
                executionId, RuntimeExecutionStatus.SUCCEEDED, null, null, null));
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT status,next_event_sequence FROM ai_runtime_execution WHERE id=%d
                     """.formatted(executionId))) {
            result.next();
            assertEquals("CREATED", result.getString("status"));
            assertEquals(2, result.getLong("next_event_sequence"));
        }
    }

    @Test
    void rejectsRawOrMalformedTraceFieldsBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> store.createExecution(new CreateRuntimeExecution(
                "bad id with spaces", "trace-invalid-fields", null, null, 1, null,
                RuntimeType.WORKFLOW, null, null, null, null, "[]")));
        assertThrows(IllegalArgumentException.class,
                () -> store.appendEvent(1, null, "CUSTOM_EVENT", "{\"rawInput\":\"secret\"}"));
    }

    @Test
    void recordsProviderUsageCostAndFallbackChain() throws Exception {
        long executionId = store.createExecution(new CreateRuntimeExecution(
                "exec-provider-1", "trace-provider-1", null, null, 103, null,
                RuntimeType.WORKFLOW, null, null, null, null, "{}"));
        long nodeId = store.createNode(new CreateRuntimeExecutionNode(
                executionId, null, "model-call", RuntimeExecutionNodeType.MODEL,
                null, null, null, null, 1, null, null, "{}"));
        long first = store.createProviderInvocation(new CreateRuntimeProviderInvocation(
                "invocation-provider-1", nodeId, providerId, modelId, "deepseek", "trace-test-model",
                RuntimeCredentialSource.PLATFORM, 1, null, "{}"));
        store.transitionProviderInvocation(first, RuntimeProviderInvocationStatus.RUNNING, null);
        store.transitionProviderInvocation(first, RuntimeProviderInvocationStatus.TIMEOUT,
                new RuntimeProviderInvocationResult(120, 0, null, null, 30000L, null,
                        "PROVIDER_TIMEOUT", "provider timeout", "{}"));
        long fallback = store.createProviderInvocation(new CreateRuntimeProviderInvocation(
                "invocation-provider-2", nodeId, providerId, modelId, "deepseek", "trace-test-model",
                RuntimeCredentialSource.PLATFORM, 2, first, "{\"fallbackReason\":\"timeout\"}"));
        store.transitionProviderInvocation(fallback, RuntimeProviderInvocationStatus.SUCCEEDED,
                new RuntimeProviderInvocationResult(120, 80, new BigDecimal("0.00250000"), "USD",
                        900L, "provider-request-safe-id", null, null, "{\"cached\":false}"));

        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT status,input_units,output_units,estimated_cost,currency,latency_ms,fallback_from_id
                     FROM ai_runtime_provider_invocation WHERE id=%d
                     """.formatted(fallback))) {
            result.next();
            assertEquals("SUCCEEDED", result.getString("status"));
            assertEquals(120, result.getLong("input_units"));
            assertEquals(80, result.getLong("output_units"));
            assertEquals(new BigDecimal("0.00250000"), result.getBigDecimal("estimated_cost"));
            assertEquals("USD", result.getString("currency"));
            assertEquals(900, result.getLong("latency_ms"));
            assertEquals(first, result.getLong("fallback_from_id"));
        }
    }
}
