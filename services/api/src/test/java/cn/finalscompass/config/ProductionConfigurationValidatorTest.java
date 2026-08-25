package cn.finalscompass.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationValidatorTest {
  @Test
  void developmentAllowsMissingProductionSecrets() {
    var environment = new MockEnvironment().withProperty("app.environment", "dev");

    assertThatCode(() -> validator(environment).validate())
        .doesNotThrowAnyException();
  }

  @Test
  void productionRejectsMissingAndExampleConfiguration() {
    var environment = validProductionEnvironment()
        .withProperty("spring.datasource.password", "replace-with-password")
        .withProperty("app.browser-bridge.allowed-origin-patterns", "chrome-extension://*");

    assertThatThrownBy(() -> validator(environment).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DB_PASSWORD still contains an example value")
        .hasMessageContaining("BROWSER_BRIDGE_ALLOWED_ORIGINS");
  }

  @Test
  void productionAcceptsValidConfigurationWithOptionalGraphDisabled() {
    assertThatCode(
            () -> validator(validProductionEnvironment()).validate())
        .doesNotThrowAnyException();
  }

  @Test
  void productionRejectsPartialMicrosoftGraphConfiguration() {
    var environment = validProductionEnvironment()
        .withProperty("app.mail.microsoft.client-id", "client-id");

    assertThatThrownBy(() -> validator(environment).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MICROSOFT_MAIL_CLIENT_ID")
        .hasMessageContaining("MICROSOFT_MAIL_CLIENT_SECRET");
  }

  private MockEnvironment validProductionEnvironment() {
    String key = Base64.getEncoder().encodeToString(new byte[32]);
    return new MockEnvironment()
        .withProperty("app.environment", "prod")
        .withProperty("spring.datasource.password", "database-password-123")
        .withProperty("spring.data.redis.password", "redis-password-12345")
        .withProperty("app.mail.verification-pepper", "0123456789abcdef0123456789abcdef")
        .withProperty("app.ai.encryption-key", key)
        .withProperty("app.mail.encryption-key", key)
        .withProperty("app.ai.document-worker.url", "http://markitdown-worker:8090")
        .withProperty("app.ai.document-worker.token", "0123456789abcdef0123456789abcdef")
        .withProperty("app.bootstrap-admin.username", "")
        .withProperty("app.bootstrap-admin.password", "")
        .withProperty(
            "app.browser-bridge.allowed-origin-patterns",
            "http://203.0.113.20,chrome-extension://abcdefghijklmnop")
        .withProperty("app.mail.login-url", "http://203.0.113.20")
        .withProperty("app.mail.frontend-url", "http://203.0.113.20")
        .withProperty("app.mail.microsoft.client-id", "")
        .withProperty("app.mail.microsoft.client-secret", "")
        .withProperty("app.mail.microsoft.redirect-uri", "");
  }

  private ProductionConfigurationValidator validator(MockEnvironment environment) {
    var validator = new ProductionConfigurationValidator();
    validator.setEnvironment(environment);
    return validator;
  }
}
