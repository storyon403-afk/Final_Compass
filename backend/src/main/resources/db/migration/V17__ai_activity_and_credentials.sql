CREATE TABLE activity_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  points INT NOT NULL,
  source_type VARCHAR(40) NOT NULL,
  source_ref VARCHAR(160) NOT NULL,
  event_date DATE NOT NULL,
  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_activity_event_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT chk_activity_event_points CHECK (points > 0),
  UNIQUE KEY uk_activity_event_dedupe (user_id, event_type, source_type, source_ref),
  INDEX idx_activity_event_ranking (event_date, user_id, points)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_monthly_entitlement (
  entitlement_month DATE NOT NULL,
  user_id BIGINT NOT NULL,
  source_month DATE NOT NULL,
  activity_score INT NOT NULL,
  ranking_position INT NOT NULL,
  granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (entitlement_month, user_id),
  CONSTRAINT fk_ai_entitlement_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT chk_ai_entitlement_rank CHECK (ranking_position BETWEEN 1 AND 20),
  INDEX idx_ai_entitlement_rank (entitlement_month, ranking_position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_ai_secret (
  user_id BIGINT NOT NULL,
  provider VARCHAR(40) NOT NULL,
  encrypted_key TEXT NOT NULL,
  encryption_iv VARCHAR(64) NOT NULL,
  key_fingerprint CHAR(12) NOT NULL,
  key_label VARCHAR(80),
  consent_version VARCHAR(20) NOT NULL,
  consented_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, provider),
  CONSTRAINT fk_user_ai_secret_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_ai_config (
  provider VARCHAR(40) PRIMARY KEY,
  encrypted_key TEXT NOT NULL,
  encryption_iv VARCHAR(64) NOT NULL,
  key_fingerprint CHAR(12) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  updated_by BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_platform_ai_config_admin FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_usage_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  provider VARCHAR(40) NOT NULL,
  model_name VARCHAR(120),
  skill_id VARCHAR(80) NOT NULL,
  credential_source ENUM('PLATFORM', 'STORED_BYOK', 'EPHEMERAL_BYOK') NOT NULL,
  status ENUM('ACCEPTED', 'SUCCEEDED', 'FAILED', 'REJECTED') NOT NULL,
  input_units INT NOT NULL DEFAULT 0,
  output_units INT NOT NULL DEFAULT 0,
  error_code VARCHAR(80),
  trace_id VARCHAR(80),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  INDEX idx_ai_usage_user_time (user_id, created_at),
  INDEX idx_ai_usage_month_source (created_at, credential_source, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
