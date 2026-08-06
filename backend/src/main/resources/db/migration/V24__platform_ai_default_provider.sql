CREATE TABLE platform_ai_setting (
  id TINYINT PRIMARY KEY,
  default_provider VARCHAR(40) NULL,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_platform_ai_setting_singleton CHECK (id = 1),
  CONSTRAINT fk_platform_ai_setting_admin FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO platform_ai_setting(id, default_provider)
SELECT 1, provider FROM platform_ai_config
WHERE enabled=TRUE AND provider <> 'gemini'
ORDER BY CASE provider WHEN 'deepseek' THEN 0 WHEN 'openai' THEN 1 ELSE 2 END
LIMIT 1;

INSERT IGNORE INTO platform_ai_setting(id, default_provider) VALUES (1, NULL);
