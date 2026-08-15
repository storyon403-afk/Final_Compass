package cn.finalscompass.ai.runtime.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRuntimeProviderDefinitionRepositoryTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("finals").withUsername("finals").withPassword("finals-test");
    private static JdbcRuntimeProviderDefinitionRepository repository;

    @BeforeAll
    static void migrateAroundLegacyProviderConfiguration() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("27").load().migrate();
        try (Connection connection = MYSQL.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO app_user(username,password_hash,display_name,role)
                    VALUES ('provider-admin','unused','Provider Admin','ADMIN')
                    """);
            statement.executeUpdate("""
                    INSERT INTO platform_ai_config(
                      provider,encrypted_key,encryption_iv,key_fingerprint,model_name,enabled,updated_by)
                    SELECT 'deepseek','cipher','iv','123456789012','deepseek-test-model',TRUE,id
                    FROM app_user WHERE username='provider-admin'
                    """);
        }
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        ObjectMapper json = new ObjectMapper();
        repository = new JdbcRuntimeProviderDefinitionRepository(
                JdbcClient.create(dataSource), json, new RuntimeProviderDefinitionValidator(json));
    }

    @Test
    void assemblesRoutableProviderModelEndpointAndCapabilities() {
        RuntimeProviderDefinition deepseek = repository.findRoutableByKey("deepseek").orElseThrow();
        assertEquals(1, deepseek.endpoints().size());
        RuntimeProviderModel configuredModel = deepseek.models().stream()
                .filter(model -> model.key().equals("deepseek-test-model"))
                .findFirst().orElseThrow();
        assertEquals(Set.of("TEXT_REASONING"), configuredModel.capabilities());
        assertEquals(Set.of("PLATFORM", "STORED_BYOK", "EPHEMERAL_BYOK"),
                deepseek.supportedCredentialSources());
        assertEquals(3, repository.findRoutable().size());
    }

    @Test
    void matcherUsesDatabaseCapabilitiesRatherThanProviderName() {
        RuntimeProviderMatcher matcher = new RuntimeProviderMatcher(repository);
        var reasoningCandidates = matcher.match(new ProviderSelectionRequest(
                Set.of("TEXT_REASONING"), 0, 0, false, false,
                Set.of(), Set.of(), "PLATFORM"));
        assertTrue(!reasoningCandidates.isEmpty());
        assertTrue(reasoningCandidates.stream()
                .allMatch(candidate -> candidate.model().capabilities().contains("TEXT_REASONING")));
        assertTrue(matcher.match(new ProviderSelectionRequest(
                Set.of("VISION"), 0, 0, false, false,
                Set.of(), Set.of(), "PLATFORM")).isEmpty());
    }

    @Test
    void blankProviderKeyReturnsEmpty() {
        assertTrue(repository.findRoutableByKey(" ").isEmpty());
    }
}
