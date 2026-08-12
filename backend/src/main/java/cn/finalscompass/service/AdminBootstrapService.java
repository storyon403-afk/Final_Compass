package cn.finalscompass.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first administrator only when explicitly configured by the operator. */
@Component
public class AdminBootstrapService implements ApplicationRunner {
  private final JdbcClient jdbc;
  private final String username;
  private final String password;
  private final String displayName;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

  public AdminBootstrapService(
      JdbcClient jdbc,
      @Value("${app.bootstrap-admin.username:}") String username,
      @Value("${app.bootstrap-admin.password:}") String password,
      @Value("${app.bootstrap-admin.display-name:Administrator}") String displayName) {
    this.jdbc = jdbc;
    this.username = username.trim();
    this.password = password;
    this.displayName = displayName.trim();
  }

  @Override
  public void run(ApplicationArguments args) {
    if (username.isBlank() && password.isBlank()) return;
    if (username.isBlank() || password.isBlank()) {
      throw new IllegalStateException(
          "APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD must be configured together");
    }
    if (username.length() > 40 || displayName.isBlank() || displayName.length() > 40) {
      throw new IllegalStateException("Bootstrap administrator name is invalid");
    }
    if (password.length() < 12) {
      throw new IllegalStateException("APP_ADMIN_PASSWORD must contain at least 12 characters");
    }

    boolean exists =
        jdbc.sql("SELECT COUNT(*) FROM app_user WHERE username=:username")
                .param("username", username)
                .query(Integer.class)
                .single()
            > 0;
    if (exists) return;

    jdbc.sql(
            """
            INSERT INTO app_user(username,password_hash,display_name,role,active)
            VALUES (:username,:password,:displayName,'ADMIN',TRUE)
            """)
        .param("username", username)
        .param("password", passwords.encode(password))
        .param("displayName", displayName)
        .update();
  }
}
