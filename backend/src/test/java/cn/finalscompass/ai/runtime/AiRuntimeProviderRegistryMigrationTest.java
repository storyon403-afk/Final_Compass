package cn.finalscompass.ai.runtime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class AiRuntimeProviderRegistryMigrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("finals")
            .withUsername("finals")
            .withPassword("finals-test");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void createsNormalizedProviderRegistry() throws SQLException {
        try (Connection connection = MYSQL.createConnection("");
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema=DATABASE() AND table_name IN (
                       'ai_runtime_provider','ai_runtime_provider_endpoint','ai_runtime_provider_model',
                       'ai_runtime_capability','ai_runtime_provider_model_capability')
                     """)) {
            result.next();
            assertEquals(5, result.getInt(1));
        }
    }

    @Test
    void rejectsInvalidProviderAndUnsafeBrowserAdapter() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            assertThrows(SQLException.class,
                    () -> insertProvider(connection, "Invalid_Key", "API", "openai-responses-v1"));
            assertThrows(SQLException.class,
                    () -> insertProvider(connection, "browser-kimi", "BROWSER", "openai-responses-v1"));
        }
    }

    @Test
    void modelCapabilityRequiresRegisteredModelAndCapability() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            long provider = insertProvider(connection, "migration-provider", "API", "openai-responses-v1");
            long model = insertModel(connection, provider, "migration-model");
            long capability = insertCapability(connection, "TEXT_REASONING");

            try (var statement = connection.prepareStatement("""
                    INSERT INTO ai_runtime_provider_model_capability(
                      provider_model_id,capability_id,configuration)
                    VALUES (?,?,JSON_OBJECT())
                    """)) {
                statement.setLong(1, model);
                statement.setLong(2, capability);
                assertEquals(1, statement.executeUpdate());
                assertThrows(SQLException.class, statement::executeUpdate);
            }
        }
    }

    @Test
    void rejectsInvalidEndpointTimeoutAndNegativeModelPrice() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            long provider = insertProvider(connection, "migration-limits-provider", "LOCAL",
                    "local-openai-compatible-v1");
            try (var endpoint = connection.prepareStatement("""
                    INSERT INTO ai_runtime_provider_endpoint(
                      provider_id,endpoint_key,base_url,connect_timeout_ms,request_timeout_ms,configuration)
                    VALUES (?,'default','http://127.0.0.1:11434',9000,1000,JSON_OBJECT())
                    """)) {
                endpoint.setLong(1, provider);
                assertThrows(SQLException.class, endpoint::executeUpdate);
            }
            try (var model = connection.prepareStatement("""
                    INSERT INTO ai_runtime_provider_model(
                      provider_id,model_key,display_name,input_unit_price,currency,configuration)
                    VALUES (?,'invalid-price','Invalid price',-1,'CNY',JSON_OBJECT())
                    """)) {
                model.setLong(1, provider);
                assertThrows(SQLException.class, model::executeUpdate);
            }
        }
    }

    private static long insertProvider(Connection connection, String key, String type, String adapter)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_provider(
                  provider_key,name,provider_type,adapter_key,credential_policy,configuration)
                VALUES (?,?,?,?,JSON_OBJECT(),JSON_OBJECT())
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, key);
            statement.setString(2, key);
            statement.setString(3, type);
            statement.setString(4, adapter);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertModel(Connection connection, long providerId, String key) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_provider_model(provider_id,model_key,display_name,configuration)
                VALUES (?,?,?,JSON_OBJECT())
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, providerId);
            statement.setString(2, key);
            statement.setString(3, key);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertCapability(Connection connection, String key) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO ai_runtime_capability(capability_key,name,description)
                VALUES (?,?,'migration test')
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, key);
            statement.setString(2, key);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }
}
