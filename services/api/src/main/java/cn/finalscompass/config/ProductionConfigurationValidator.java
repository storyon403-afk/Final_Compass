package cn.finalscompass.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** 在创建数据源、Web Server 和业务 Bean 前拒绝不安全的生产配置。 */
@Component
public final class ProductionConfigurationValidator
    implements BeanFactoryPostProcessor, EnvironmentAware {
  private static final List<String> PLACEHOLDER_MARKERS =
      List.of("replace-with", "change-me", "your-domain", "example.com", "replace_with");

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    validate();
  }

  void validate() {
    if (!isProduction()) return;

    List<String> errors = new ArrayList<>();
    requireSecret(errors, "spring.datasource.password", "DB_PASSWORD", 12);
    requireSecret(errors, "spring.data.redis.password", "REDIS_PASSWORD", 16);
    requireSecret(errors, "app.mail.verification-pepper", "EMAIL_CODE_PEPPER", 32);
    requireBase64Key(errors, "app.ai.encryption-key", "AI_SECRET_ENCRYPTION_KEY");
    requireBase64Key(errors, "app.mail.encryption-key", "MAIL_SECRET_ENCRYPTION_KEY");
    validateDocumentWorker(errors);
    validateBootstrapAdmin(errors);
    validateOrigins(errors);
    validateUrl(errors, "app.mail.login-url", "APP_LOGIN_URL");
    validateUrl(errors, "app.mail.frontend-url", "APP_FRONTEND_URL");
    validateMicrosoftGraph(errors);

    if (!errors.isEmpty()) {
      throw new IllegalStateException(
          "Production configuration is invalid:\n - " + String.join("\n - ", errors));
    }
  }

  private boolean isProduction() {
    return "prod".equalsIgnoreCase(value("app.environment"))
        || environment.acceptsProfiles(Profiles.of("prod"));
  }

  private void requireSecret(
      List<String> errors, String property, String environmentName, int minimumLength) {
    String configured = value(property);
    if (configured.isBlank()) {
      errors.add(environmentName + " must not be empty");
      return;
    }
    if (configured.length() < minimumLength) {
      errors.add(environmentName + " must contain at least " + minimumLength + " characters");
    }
    if (isPlaceholder(configured)) {
      errors.add(environmentName + " still contains an example value");
    }
  }

  private void requireBase64Key(
      List<String> errors, String property, String environmentName) {
    String configured = value(property);
    if (configured.isBlank()) {
      errors.add(environmentName + " must not be empty");
      return;
    }
    if (isPlaceholder(configured)) {
      errors.add(environmentName + " still contains an example value");
      return;
    }
    try {
      if (Base64.getDecoder().decode(configured).length != 32) {
        errors.add(environmentName + " must decode to exactly 32 bytes");
      }
    } catch (IllegalArgumentException exception) {
      errors.add(environmentName + " must be valid Base64");
    }
  }

  private void validateDocumentWorker(List<String> errors) {
    if (!value("app.ai.document-worker.url").isBlank()) {
      requireSecret(
          errors, "app.ai.document-worker.token", "MARKITDOWN_WORKER_TOKEN", 32);
    }
  }

  private void validateBootstrapAdmin(List<String> errors) {
    String username = value("app.bootstrap-admin.username");
    String password = value("app.bootstrap-admin.password");
    if (username.isBlank() && password.isBlank()) return;
    if (username.isBlank() || password.isBlank()) {
      errors.add(
          "APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD must either both be set or both be empty");
      return;
    }
    if (password.length() < 12 || isPlaceholder(password)) {
      errors.add("APP_ADMIN_PASSWORD must be a non-example password of at least 12 characters");
    }
  }

  private void validateOrigins(List<String> errors) {
    String origins = value("app.browser-bridge.allowed-origin-patterns");
    if (origins.isBlank()) {
      errors.add("BROWSER_BRIDGE_ALLOWED_ORIGINS must not be empty");
      return;
    }
    for (String origin : origins.split(",")) {
      String candidate = origin.trim();
      if (candidate.isBlank() || candidate.contains("*") || isPlaceholder(candidate)) {
        errors.add(
            "BROWSER_BRIDGE_ALLOWED_ORIGINS must contain exact, non-example origins without wildcards");
        return;
      }
    }
  }

  private void validateUrl(List<String> errors, String property, String environmentName) {
    String configured = value(property);
    if (configured.isBlank() || isPlaceholder(configured)) {
      errors.add(environmentName + " must contain the deployed site URL");
      return;
    }
    try {
      URI uri = URI.create(configured);
      String scheme = uri.getScheme();
      if (uri.getHost() == null
          || (!("http".equalsIgnoreCase(scheme)) && !("https".equalsIgnoreCase(scheme)))) {
        errors.add(environmentName + " must be an absolute HTTP or HTTPS URL");
      }
    } catch (IllegalArgumentException exception) {
      errors.add(environmentName + " is not a valid URL");
    }
  }

  private void validateMicrosoftGraph(List<String> errors) {
    String clientId = value("app.mail.microsoft.client-id");
    String clientSecret = value("app.mail.microsoft.client-secret");
    String redirectUri = value("app.mail.microsoft.redirect-uri");
    if (clientId.isBlank() && clientSecret.isBlank()) return;
    if (clientId.isBlank() || clientSecret.isBlank()) {
      errors.add(
          "MICROSOFT_MAIL_CLIENT_ID and MICROSOFT_MAIL_CLIENT_SECRET must be configured together");
      return;
    }
    if (isPlaceholder(clientId) || isPlaceholder(clientSecret)) {
      errors.add("Microsoft Graph credentials still contain an example value");
    }
    if (redirectUri.isBlank()) {
      errors.add("MICROSOFT_MAIL_REDIRECT_URI is required when Microsoft Graph is enabled");
    } else {
      validateUrl(errors, "app.mail.microsoft.redirect-uri", "MICROSOFT_MAIL_REDIRECT_URI");
    }
  }

  private boolean isPlaceholder(String configured) {
    String normalized = configured.toLowerCase(Locale.ROOT);
    return PLACEHOLDER_MARKERS.stream().anyMatch(normalized::contains);
  }

  private String value(String property) {
    return environment.getProperty(property, "").trim();
  }
}
