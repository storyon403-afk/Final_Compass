CREATE TABLE ai_runtime_tool (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tool_key VARCHAR(120) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
  current_version_id BIGINT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_runtime_tool_key UNIQUE (tool_key),
  CONSTRAINT chk_ai_runtime_tool_key CHECK (
    tool_key REGEXP '^[A-Za-z][A-Za-z0-9]*([._-][A-Za-z0-9]+)*$'
  ),
  CONSTRAINT chk_ai_runtime_tool_status CHECK (status IN ('ACTIVE','DEPRECATED','DISABLED')),
  CONSTRAINT chk_ai_runtime_tool_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
  INDEX idx_ai_runtime_tool_status (status,tool_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_tool_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tool_id BIGINT NOT NULL,
  version VARCHAR(32) NOT NULL,
  lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  transport_type VARCHAR(20) NOT NULL,
  executor_key VARCHAR(120) NOT NULL,
  input_schema JSON NOT NULL,
  output_schema JSON NOT NULL,
  permission_policy JSON NOT NULL,
  configuration JSON NOT NULL,
  timeout_ms INT NOT NULL DEFAULT 30000,
  max_result_bytes INT NOT NULL DEFAULT 1048576,
  checksum CHAR(64) NULL,
  created_by BIGINT NULL,
  published_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL,
  retired_at TIMESTAMP NULL,
  CONSTRAINT fk_ai_runtime_tool_version_tool FOREIGN KEY (tool_id) REFERENCES ai_runtime_tool(id),
  CONSTRAINT uk_ai_runtime_tool_version UNIQUE (tool_id,version),
  CONSTRAINT uk_ai_runtime_tool_version_identity UNIQUE (id,tool_id),
  CONSTRAINT chk_ai_runtime_tool_version_format CHECK (
    version REGEXP '^[0-9]+[.][0-9]+[.][0-9]+([+-][0-9A-Za-z.-]+)?$'
  ),
  CONSTRAINT chk_ai_runtime_tool_version_lifecycle CHECK (
    lifecycle_status IN ('DRAFT','PUBLISHED','RETIRED')
  ),
  CONSTRAINT chk_ai_runtime_tool_transport CHECK (
    transport_type IN ('INTERNAL','MCP','HTTP','BROWSER')
  ),
  CONSTRAINT chk_ai_runtime_tool_limits CHECK (
    timeout_ms BETWEEN 100 AND 300000 AND max_result_bytes BETWEEN 1 AND 8388608
  ),
  CONSTRAINT chk_ai_runtime_tool_json_shapes CHECK (
    JSON_TYPE(input_schema)='OBJECT' AND JSON_TYPE(output_schema)='OBJECT'
    AND JSON_TYPE(permission_policy)='OBJECT' AND JSON_TYPE(configuration)='OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_tool_version_publish CHECK (
    (lifecycle_status='DRAFT' AND published_at IS NULL AND retired_at IS NULL AND checksum IS NULL)
    OR (lifecycle_status='PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL
      AND checksum REGEXP '^[0-9a-f]{64}$')
    OR (lifecycle_status='RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL
      AND checksum REGEXP '^[0-9a-f]{64}$')
  ),
  INDEX idx_ai_runtime_tool_version_lifecycle (tool_id,lifecycle_status,published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ai_runtime_tool
  ADD CONSTRAINT fk_ai_runtime_tool_current_version
  FOREIGN KEY (current_version_id,id) REFERENCES ai_runtime_tool_version(id,tool_id);
