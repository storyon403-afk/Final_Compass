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

@Testcontainers(disabledWithoutDocker = true)
class AiRuntimeSeedMigrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("finals")
            .withUsername("finals")
            .withPassword("finals-test");

    @BeforeAll
    static void migrateAroundLegacyConfiguration() throws SQLException {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("27").load().migrate();
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO app_user(username,password_hash,display_name,role)
                    VALUES ('seed-admin','not-used','Seed Admin','ADMIN')
                    """);
            statement.executeUpdate("""
                    INSERT INTO platform_ai_config(
                      provider,encrypted_key,encryption_iv,key_fingerprint,model_name,enabled,updated_by)
                    SELECT 'deepseek','cipher','iv','123456789012','configured-model',TRUE,id
                    FROM app_user WHERE username='seed-admin'
                    """);
            statement.executeUpdate("""
                    INSERT INTO platform_ai_config(
                      provider,encrypted_key,encryption_iv,key_fingerprint,model_name,enabled,updated_by)
                    SELECT 'hermes','cipher','iv','abcdefghijkl','hermes-agent',TRUE,id
                    FROM app_user WHERE username='seed-admin'
                    """);
        }
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void seedsAllLegacySkillsWithPublishedCurrentVersions() throws SQLException {
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) total,
                       SUM(current_version_id IS NOT NULL) current_versions
                     FROM ai_runtime_skill
                     """)) {
            result.next();
            assertEquals(11, result.getInt("total"));
            assertEquals(11, result.getInt("current_versions"));
        }
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM ai_runtime_skill_version
                     WHERE lifecycle_status='PUBLISHED' AND checksum<>REPEAT('0',64)
                     """)) {
            result.next();
            assertEquals(11, result.getInt(1));
        }
    }

    @Test
    void importsConfiguredModelButDoesNotRegisterHermesAsProvider() throws SQLException {
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM ai_runtime_provider_model m
                     JOIN ai_runtime_provider p ON p.id=m.provider_id
                     WHERE p.provider_key='deepseek' AND m.model_key='configured-model'
                     """)) {
            result.next();
            assertEquals(1, result.getInt(1));
        }
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT COUNT(*) FROM ai_runtime_provider WHERE provider_key='hermes'")) {
            result.next();
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void preservesLegacyCategoryAndImageModalitiesForCompatibility() throws SQLException {
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT JSON_UNQUOTE(JSON_EXTRACT(v.configuration,'$.legacyCategory')) category,
                       JSON_CONTAINS(JSON_EXTRACT(v.configuration,'$.modalities'),JSON_QUOTE('IMAGE')) image_enabled
                     FROM ai_runtime_skill s
                     JOIN ai_runtime_skill_version v ON v.id=s.current_version_id
                     WHERE s.skill_key='solution-review'
                     """)) {
            result.next();
            assertEquals("LEARNING", result.getString("category"));
            assertEquals(1, result.getInt("image_enabled"));
        }
    }
}
