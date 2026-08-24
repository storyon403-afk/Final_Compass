ALTER TABLE beta_access_request
  MODIFY verification_code CHAR(6) NULL,
  MODIFY status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  ADD COLUMN last_code_sent_at TIMESTAMP NULL,
  ADD COLUMN reviewed_by BIGINT NULL,
  ADD COLUMN reviewed_at TIMESTAMP NULL,
  ADD COLUMN rejection_reason VARCHAR(300) NULL,
  ADD CONSTRAINT fk_beta_access_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user(id);

UPDATE beta_access_request SET verification_code=NULL;

ALTER TABLE app_user
  ADD COLUMN email VARCHAR(254) NULL,
  ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  ADD UNIQUE KEY uk_app_user_email (email);

CREATE TABLE smtp_configuration (
  id BIGINT PRIMARY KEY,
  host VARCHAR(255) NOT NULL,
  port INT NOT NULL,
  security_mode VARCHAR(20) NOT NULL,
  username VARCHAR(254) NOT NULL,
  encrypted_credential TEXT NOT NULL,
  credential_iv VARCHAR(64) NOT NULL,
  credential_fingerprint CHAR(12) NOT NULL,
  from_address VARCHAR(254) NOT NULL,
  from_name VARCHAR(100) NOT NULL,
  reply_to VARCHAR(254),
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  last_tested_at TIMESTAMP NULL,
  last_test_status VARCHAR(20),
  updated_by BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_smtp_updated_by FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_type VARCHAR(40) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  subject_template VARCHAR(200) NOT NULL,
  text_template TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_email_template_type (template_type),
  CONSTRAINT fk_email_template_admin FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO email_template(template_type,subject_template,text_template) VALUES
('EMAIL_VERIFICATION','Finals Compass 邮箱验证码',
'你好！\n\n你的验证码是：{{verificationCode}}\n\n验证码在 {{expiresMinutes}} 分钟内有效，请勿转发。\n\n如非本人操作，请忽略本邮件。\n\nFinals Compass'),
('ACCOUNT_CREDENTIAL','Finals Compass 内测账号已开通',
'你好！\n\n管理员已人工审核并开通你的账号。\n\n账号：{{username}}\n一次性临时密码：{{temporaryPassword}}\n\n登录地址：{{loginUrl}}\n\n首次登录后请立即修改密码。\n\nFinals Compass');

CREATE TABLE email_delivery_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT NULL,
  delivery_type VARCHAR(40) NOT NULL,
  recipient_hash CHAR(64) NOT NULL,
  template_type VARCHAR(40) NOT NULL,
  template_version INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  error_code VARCHAR(80),
  requested_by BIGINT NULL,
  sent_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_delivery_request FOREIGN KEY (request_id) REFERENCES beta_access_request(id),
  CONSTRAINT fk_delivery_admin FOREIGN KEY (requested_by) REFERENCES app_user(id),
  INDEX idx_delivery_request (request_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_provisioning (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT NOT NULL UNIQUE,
  user_id BIGINT NOT NULL UNIQUE,
  status VARCHAR(32) NOT NULL,
  reviewed_by BIGINT NOT NULL,
  reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  credential_sent_at TIMESTAMP NULL,
  last_delivery_status VARCHAR(20),
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_provision_request FOREIGN KEY (request_id) REFERENCES beta_access_request(id),
  CONSTRAINT fk_provision_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_provision_admin FOREIGN KEY (reviewed_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
