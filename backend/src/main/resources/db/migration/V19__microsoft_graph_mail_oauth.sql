CREATE TABLE mail_provider_setting (
  id BIGINT PRIMARY KEY,
  active_provider VARCHAR(32) NOT NULL DEFAULT 'SMTP',
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_mail_provider_admin FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO mail_provider_setting(id, active_provider) VALUES (1, 'SMTP');

CREATE TABLE mail_oauth_connection (
  id BIGINT PRIMARY KEY,
  provider VARCHAR(32) NOT NULL,
  account_email VARCHAR(254) NOT NULL,
  account_name VARCHAR(100),
  encrypted_refresh_token TEXT NOT NULL,
  refresh_token_iv VARCHAR(64) NOT NULL,
  token_fingerprint CHAR(12) NOT NULL,
  granted_scopes TEXT NOT NULL,
  status VARCHAR(24) NOT NULL,
  connected_by BIGINT NOT NULL,
  connected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_refreshed_at TIMESTAMP NULL,
  last_success_at TIMESTAMP NULL,
  last_error_code VARCHAR(100),
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mail_oauth_provider (provider),
  CONSTRAINT fk_mail_oauth_admin FOREIGN KEY (connected_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
