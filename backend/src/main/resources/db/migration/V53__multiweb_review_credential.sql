CREATE TABLE platform_ai_review_config (
  id TINYINT PRIMARY KEY,
  provider VARCHAR(40) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  encrypted_key TEXT NOT NULL,
  encryption_iv VARCHAR(255) NOT NULL,
  key_fingerprint VARCHAR(80) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT NOT NULL,
  updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_platform_ai_review_singleton CHECK (id = 1),
  CONSTRAINT fk_platform_ai_review_admin FOREIGN KEY (updated_by) REFERENCES app_user(id)
);

CREATE TABLE user_ai_review_secret (
  user_id BIGINT NOT NULL,
  provider VARCHAR(40) NOT NULL,
  encrypted_key TEXT NOT NULL,
  encryption_iv VARCHAR(255) NOT NULL,
  key_fingerprint VARCHAR(80) NOT NULL,
  key_label VARCHAR(80),
  consent_version VARCHAR(40) NOT NULL,
  consented_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY(user_id,provider),
  FOREIGN KEY(user_id) REFERENCES app_user(id)
);
