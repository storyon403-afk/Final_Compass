CREATE TABLE ai_runtime_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id VARCHAR(64) NOT NULL,
  trace_id VARCHAR(80) NOT NULL,
  parent_execution_id BIGINT NULL,
  legacy_task_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  session_id VARCHAR(80) NULL,
  runtime_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  goal_summary VARCHAR(1000) NULL,
  input_reference VARCHAR(255) NULL,
  result_reference VARCHAR(255) NULL,
  workflow_key VARCHAR(100) NULL,
  workflow_version VARCHAR(32) NULL,
  error_code VARCHAR(80) NULL,
  error_summary VARCHAR(500) NULL,
  metadata JSON NOT NULL,
  next_event_sequence BIGINT NOT NULL DEFAULT 1,
  started_at TIMESTAMP(6) NULL,
  completed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ai_runtime_execution_parent FOREIGN KEY (parent_execution_id)
    REFERENCES ai_runtime_execution(id),
  CONSTRAINT uk_ai_runtime_execution_external UNIQUE (execution_id),
  CONSTRAINT uk_ai_runtime_execution_trace UNIQUE (trace_id),
  CONSTRAINT uk_ai_runtime_execution_legacy_task UNIQUE (legacy_task_id),
  CONSTRAINT chk_ai_runtime_execution_runtime CHECK (
    runtime_type IN ('LEGACY','WORKFLOW','AGENT','MULTI_WEB_AGENT')
  ),
  CONSTRAINT chk_ai_runtime_execution_status CHECK (
    status IN ('CREATED','PLANNING','RUNNING','WAITING_USER','WAITING_TOOL','RETRYING',
      'SUCCEEDED','FAILED','CANCELLED')
  ),
  CONSTRAINT chk_ai_runtime_execution_metadata CHECK (
    JSON_TYPE(metadata) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_execution_sequence CHECK (
    next_event_sequence > 0
  ),
  CONSTRAINT chk_ai_runtime_execution_timing CHECK (
    (started_at IS NULL OR completed_at IS NULL OR completed_at >= started_at)
    AND ((status IN ('SUCCEEDED','FAILED','CANCELLED') AND completed_at IS NOT NULL)
      OR (status NOT IN ('SUCCEEDED','FAILED','CANCELLED') AND completed_at IS NULL))
  ),
  CONSTRAINT chk_ai_runtime_execution_error CHECK (
    (status = 'FAILED' AND error_code IS NOT NULL)
    OR (status = 'SUCCEEDED' AND error_code IS NULL)
    OR status NOT IN ('FAILED','SUCCEEDED')
  ),
  INDEX idx_ai_runtime_execution_user_created (user_id, created_at),
  INDEX idx_ai_runtime_execution_status_created (status, created_at),
  INDEX idx_ai_runtime_execution_session_created (session_id, created_at),
  INDEX idx_ai_runtime_execution_parent (parent_execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_execution_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id BIGINT NOT NULL,
  parent_node_id BIGINT NULL,
  node_key VARCHAR(100) NOT NULL,
  node_type VARCHAR(32) NOT NULL,
  skill_id BIGINT NULL,
  skill_version_id BIGINT NULL,
  skill_key_snapshot VARCHAR(100) NULL,
  skill_version_snapshot VARCHAR(32) NULL,
  attempt INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  input_reference VARCHAR(255) NULL,
  output_reference VARCHAR(255) NULL,
  input_digest CHAR(64) NULL,
  output_digest CHAR(64) NULL,
  error_code VARCHAR(80) NULL,
  error_summary VARCHAR(500) NULL,
  metadata JSON NOT NULL,
  started_at TIMESTAMP(6) NULL,
  completed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ai_runtime_execution_node_execution FOREIGN KEY (execution_id)
    REFERENCES ai_runtime_execution(id),
  CONSTRAINT fk_ai_runtime_execution_node_parent FOREIGN KEY (parent_node_id, execution_id)
    REFERENCES ai_runtime_execution_node(id, execution_id),
  CONSTRAINT fk_ai_runtime_execution_node_skill_version FOREIGN KEY (skill_version_id, skill_id)
    REFERENCES ai_runtime_skill_version(id, skill_id),
  CONSTRAINT uk_ai_runtime_execution_node_attempt UNIQUE (execution_id, node_key, attempt),
  CONSTRAINT uk_ai_runtime_execution_node_identity UNIQUE (id, execution_id),
  CONSTRAINT chk_ai_runtime_execution_node_key CHECK (
    node_key REGEXP '^[a-zA-Z][a-zA-Z0-9_-]{0,99}$'
  ),
  CONSTRAINT chk_ai_runtime_execution_node_type CHECK (
    node_type IN ('TASK_UNDERSTANDING','WORKFLOW_RESOLUTION','SKILL_RESOLUTION','SKILL','MODEL',
      'TOOL','CONDITION','PARALLEL','JOIN','HUMAN_CONFIRM','DOCUMENT','AGENT')
  ),
  CONSTRAINT chk_ai_runtime_execution_node_status CHECK (
    status IN ('PENDING','READY','RUNNING','WAITING_USER','WAITING_TOOL','RETRYING',
      'SUCCEEDED','FAILED','SKIPPED','CANCELLED')
  ),
  CONSTRAINT chk_ai_runtime_execution_node_attempt_value CHECK (
    attempt > 0
  ),
  CONSTRAINT chk_ai_runtime_execution_node_skill_snapshot CHECK (
    (skill_id IS NULL AND skill_version_id IS NULL
      AND skill_key_snapshot IS NULL AND skill_version_snapshot IS NULL)
    OR (skill_id IS NOT NULL AND skill_version_id IS NOT NULL
      AND skill_key_snapshot IS NOT NULL AND skill_version_snapshot IS NOT NULL)
  ),
  CONSTRAINT chk_ai_runtime_execution_node_digests CHECK (
    (input_digest IS NULL OR input_digest REGEXP '^[0-9a-f]{64}$')
    AND (output_digest IS NULL OR output_digest REGEXP '^[0-9a-f]{64}$')
  ),
  CONSTRAINT chk_ai_runtime_execution_node_metadata CHECK (
    JSON_TYPE(metadata) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_execution_node_timing CHECK (
    (started_at IS NULL OR completed_at IS NULL OR completed_at >= started_at)
    AND ((status IN ('SUCCEEDED','FAILED','SKIPPED','CANCELLED') AND completed_at IS NOT NULL)
      OR (status NOT IN ('SUCCEEDED','FAILED','SKIPPED','CANCELLED') AND completed_at IS NULL))
  ),
  CONSTRAINT chk_ai_runtime_execution_node_error CHECK (
    (status = 'FAILED' AND error_code IS NOT NULL)
    OR (status = 'SUCCEEDED' AND error_code IS NULL)
    OR status NOT IN ('FAILED','SUCCEEDED')
  ),
  INDEX idx_ai_runtime_execution_node_status (execution_id, status),
  INDEX idx_ai_runtime_execution_node_skill (skill_version_id, status, started_at),
  INDEX idx_ai_runtime_execution_node_parent (parent_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_provider_invocation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  invocation_id VARCHAR(64) NOT NULL,
  execution_node_id BIGINT NOT NULL,
  provider_id BIGINT NOT NULL,
  provider_model_id BIGINT NOT NULL,
  provider_key_snapshot VARCHAR(64) NOT NULL,
  model_key_snapshot VARCHAR(120) NOT NULL,
  credential_source VARCHAR(32) NOT NULL,
  attempt INT NOT NULL DEFAULT 1,
  status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
  input_units BIGINT NOT NULL DEFAULT 0,
  output_units BIGINT NOT NULL DEFAULT 0,
  estimated_cost DECIMAL(18,8) NULL,
  currency CHAR(3) NULL,
  latency_ms BIGINT NULL,
  provider_request_id VARCHAR(160) NULL,
  fallback_from_id BIGINT NULL,
  error_code VARCHAR(80) NULL,
  error_summary VARCHAR(500) NULL,
  metadata JSON NOT NULL,
  started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_ai_runtime_invocation_node FOREIGN KEY (execution_node_id)
    REFERENCES ai_runtime_execution_node(id),
  CONSTRAINT fk_ai_runtime_invocation_model FOREIGN KEY (provider_model_id, provider_id)
    REFERENCES ai_runtime_provider_model(id, provider_id),
  CONSTRAINT fk_ai_runtime_invocation_fallback FOREIGN KEY (fallback_from_id, execution_node_id)
    REFERENCES ai_runtime_provider_invocation(id, execution_node_id),
  CONSTRAINT uk_ai_runtime_invocation_external UNIQUE (invocation_id),
  CONSTRAINT uk_ai_runtime_invocation_attempt UNIQUE (execution_node_id, provider_model_id, attempt),
  CONSTRAINT uk_ai_runtime_invocation_identity UNIQUE (id, execution_node_id),
  CONSTRAINT chk_ai_runtime_invocation_credential CHECK (
    credential_source IN ('PLATFORM','STORED_BYOK','EPHEMERAL_BYOK')
  ),
  CONSTRAINT chk_ai_runtime_invocation_status CHECK (
    status IN ('ACCEPTED','RUNNING','SUCCEEDED','FAILED','TIMEOUT','CANCELLED')
  ),
  CONSTRAINT chk_ai_runtime_invocation_values CHECK (
    attempt > 0 AND input_units >= 0 AND output_units >= 0
    AND (latency_ms IS NULL OR latency_ms >= 0)
    AND (estimated_cost IS NULL OR estimated_cost >= 0)
  ),
  CONSTRAINT chk_ai_runtime_invocation_cost CHECK (
    (estimated_cost IS NULL AND currency IS NULL)
    OR (estimated_cost IS NOT NULL AND currency REGEXP '^[A-Z]{3}$')
  ),
  CONSTRAINT chk_ai_runtime_invocation_metadata CHECK (
    JSON_TYPE(metadata) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_invocation_timing CHECK (
    (completed_at IS NULL OR completed_at >= started_at)
    AND ((status IN ('SUCCEEDED','FAILED','TIMEOUT','CANCELLED') AND completed_at IS NOT NULL)
      OR (status IN ('ACCEPTED','RUNNING') AND completed_at IS NULL))
  ),
  CONSTRAINT chk_ai_runtime_invocation_error CHECK (
    (status IN ('FAILED','TIMEOUT') AND error_code IS NOT NULL)
    OR (status = 'SUCCEEDED' AND error_code IS NULL)
    OR status NOT IN ('FAILED','TIMEOUT','SUCCEEDED')
  ),
  INDEX idx_ai_runtime_invocation_model_status (provider_model_id, status, started_at),
  INDEX idx_ai_runtime_invocation_fallback (fallback_from_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_execution_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id BIGINT NOT NULL,
  execution_node_id BIGINT NULL,
  sequence_no BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_payload JSON NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ai_runtime_execution_event_execution FOREIGN KEY (execution_id)
    REFERENCES ai_runtime_execution(id),
  CONSTRAINT fk_ai_runtime_execution_event_node FOREIGN KEY (execution_node_id, execution_id)
    REFERENCES ai_runtime_execution_node(id, execution_id),
  CONSTRAINT uk_ai_runtime_execution_event_sequence UNIQUE (execution_id, sequence_no),
  CONSTRAINT chk_ai_runtime_execution_event_sequence CHECK (
    sequence_no > 0
  ),
  CONSTRAINT chk_ai_runtime_execution_event_type CHECK (
    event_type REGEXP '^[A-Z][A-Z0-9_]{2,63}$'
  ),
  CONSTRAINT chk_ai_runtime_execution_event_payload CHECK (
    JSON_TYPE(event_payload) = 'OBJECT'
  ),
  INDEX idx_ai_runtime_execution_event_type (event_type, occurred_at),
  INDEX idx_ai_runtime_execution_event_node (execution_node_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
