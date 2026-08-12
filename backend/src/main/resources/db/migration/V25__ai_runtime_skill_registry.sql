CREATE TABLE ai_runtime_skill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  skill_key VARCHAR(100) NOT NULL,
  name VARCHAR(120) NOT NULL,
  skill_type VARCHAR(32) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
  domain_tags JSON NOT NULL,
  current_version_id BIGINT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_runtime_skill_key UNIQUE (skill_key),
  CONSTRAINT chk_ai_runtime_skill_key CHECK (
    skill_key REGEXP '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'
  ),
  CONSTRAINT chk_ai_runtime_skill_type CHECK (
    skill_type IN ('PERCEPTION','REASONING','PLANNING','GENERATION','TOOL')
  ),
  CONSTRAINT chk_ai_runtime_skill_status CHECK (
    status IN ('ACTIVE','DEPRECATED','DISABLED')
  ),
  CONSTRAINT chk_ai_runtime_skill_risk CHECK (
    risk_level IN ('LOW','MEDIUM','HIGH')
  ),
  CONSTRAINT chk_ai_runtime_skill_domain_tags CHECK (
    JSON_TYPE(domain_tags) = 'ARRAY'
  ),
  INDEX idx_ai_runtime_skill_status_type (status, skill_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_skill_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  skill_id BIGINT NOT NULL,
  version VARCHAR(32) NOT NULL,
  lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  executor_type VARCHAR(32) NOT NULL,
  executor_key VARCHAR(80) NOT NULL,
  input_schema JSON NOT NULL,
  output_schema JSON NOT NULL,
  prompt_template MEDIUMTEXT NULL,
  output_contract TEXT NULL,
  required_capabilities JSON NOT NULL,
  permission_policy JSON NOT NULL,
  allowed_tools JSON NOT NULL,
  configuration JSON NOT NULL,
  max_input_units INT NOT NULL,
  timeout_ms INT NOT NULL,
  retry_policy JSON NOT NULL,
  checksum CHAR(64) NULL,
  created_by BIGINT NULL,
  published_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL,
  retired_at TIMESTAMP NULL,
  CONSTRAINT fk_ai_runtime_skill_version_skill FOREIGN KEY (skill_id)
    REFERENCES ai_runtime_skill(id),
  CONSTRAINT uk_ai_runtime_skill_version UNIQUE (skill_id, version),
  CONSTRAINT uk_ai_runtime_skill_version_identity UNIQUE (id, skill_id),
  CONSTRAINT chk_ai_runtime_skill_version_format CHECK (
    version REGEXP '^[0-9]+[.][0-9]+[.][0-9]+([+-][0-9A-Za-z.-]+)?$'
  ),
  CONSTRAINT chk_ai_runtime_skill_version_lifecycle CHECK (
    lifecycle_status IN ('DRAFT','PUBLISHED','RETIRED')
  ),
  CONSTRAINT chk_ai_runtime_skill_version_executor CHECK (
    executor_type IN ('LLM_PROMPT','INTERNAL','HTTP','MCP','DOCUMENT')
  ),
  CONSTRAINT chk_ai_runtime_skill_version_limits CHECK (
    max_input_units > 0 AND timeout_ms BETWEEN 100 AND 3600000
  ),
  CONSTRAINT chk_ai_runtime_skill_version_json_shapes CHECK (
    JSON_TYPE(input_schema) = 'OBJECT'
    AND JSON_TYPE(output_schema) = 'OBJECT'
    AND JSON_TYPE(required_capabilities) = 'ARRAY'
    AND JSON_TYPE(permission_policy) = 'OBJECT'
    AND JSON_TYPE(allowed_tools) = 'ARRAY'
    AND JSON_TYPE(configuration) = 'OBJECT'
    AND JSON_TYPE(retry_policy) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_skill_version_checksum CHECK (
    checksum IS NULL OR checksum REGEXP '^[0-9a-f]{64}$'
  ),
  CONSTRAINT chk_ai_runtime_skill_version_publish CHECK (
    (lifecycle_status = 'DRAFT' AND published_at IS NULL AND retired_at IS NULL AND checksum IS NULL)
    OR (lifecycle_status = 'PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL AND checksum IS NOT NULL)
    OR (lifecycle_status = 'RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL AND checksum IS NOT NULL)
  ),
  INDEX idx_ai_runtime_skill_version_lifecycle (skill_id, lifecycle_status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ai_runtime_skill
  ADD CONSTRAINT fk_ai_runtime_skill_current_version
  FOREIGN KEY (current_version_id, id)
  REFERENCES ai_runtime_skill_version(id, skill_id);
