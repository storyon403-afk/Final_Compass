CREATE TABLE ai_runtime_workflow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_key VARCHAR(100) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  runtime_type VARCHAR(32) NOT NULL DEFAULT 'WORKFLOW',
  resolver_tags JSON NOT NULL,
  current_version_id BIGINT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_runtime_workflow_key UNIQUE (workflow_key),
  CONSTRAINT chk_ai_runtime_workflow_key CHECK (
    workflow_key REGEXP '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'
  ),
  CONSTRAINT chk_ai_runtime_workflow_status CHECK (
    status IN ('ACTIVE','DEPRECATED','DISABLED')
  ),
  CONSTRAINT chk_ai_runtime_workflow_runtime CHECK (
    runtime_type IN ('WORKFLOW','AGENT','MULTI_WEB_AGENT')
  ),
  CONSTRAINT chk_ai_runtime_workflow_tags CHECK (
    JSON_TYPE(resolver_tags) = 'ARRAY'
  ),
  INDEX idx_ai_runtime_workflow_status_runtime (status, runtime_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_workflow_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_id BIGINT NOT NULL,
  version VARCHAR(32) NOT NULL,
  lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  entry_node_key VARCHAR(100) NULL,
  input_schema JSON NOT NULL,
  output_schema JSON NOT NULL,
  resolver_policy JSON NOT NULL,
  session_policy JSON NOT NULL,
  max_parallelism INT NOT NULL DEFAULT 1,
  timeout_ms INT NOT NULL,
  checksum CHAR(64) NULL,
  created_by BIGINT NULL,
  published_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP NULL,
  retired_at TIMESTAMP NULL,
  CONSTRAINT fk_ai_runtime_workflow_version_workflow FOREIGN KEY (workflow_id)
    REFERENCES ai_runtime_workflow(id),
  CONSTRAINT uk_ai_runtime_workflow_version UNIQUE (workflow_id, version),
  CONSTRAINT uk_ai_runtime_workflow_version_identity UNIQUE (id, workflow_id),
  CONSTRAINT chk_ai_runtime_workflow_version_format CHECK (
    version REGEXP '^[0-9]+[.][0-9]+[.][0-9]+([+-][0-9A-Za-z.-]+)?$'
  ),
  CONSTRAINT chk_ai_runtime_workflow_version_lifecycle CHECK (
    lifecycle_status IN ('DRAFT','PUBLISHED','RETIRED')
  ),
  CONSTRAINT chk_ai_runtime_workflow_version_json CHECK (
    JSON_TYPE(input_schema) = 'OBJECT'
    AND JSON_TYPE(output_schema) = 'OBJECT'
    AND JSON_TYPE(resolver_policy) = 'OBJECT'
    AND JSON_TYPE(session_policy) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_workflow_version_limits CHECK (
    max_parallelism BETWEEN 1 AND 64 AND timeout_ms BETWEEN 100 AND 86400000
  ),
  CONSTRAINT chk_ai_runtime_workflow_version_checksum CHECK (
    checksum IS NULL OR checksum REGEXP '^[0-9a-f]{64}$'
  ),
  CONSTRAINT chk_ai_runtime_workflow_version_publish CHECK (
    (lifecycle_status = 'DRAFT' AND entry_node_key IS NULL
      AND published_at IS NULL AND retired_at IS NULL AND checksum IS NULL)
    OR (lifecycle_status = 'PUBLISHED' AND entry_node_key IS NOT NULL
      AND published_at IS NOT NULL AND retired_at IS NULL AND checksum IS NOT NULL)
    OR (lifecycle_status = 'RETIRED' AND entry_node_key IS NOT NULL
      AND published_at IS NOT NULL AND retired_at IS NOT NULL AND checksum IS NOT NULL)
  ),
  INDEX idx_ai_runtime_workflow_version_lifecycle (workflow_id, lifecycle_status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_workflow_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_version_id BIGINT NOT NULL,
  node_key VARCHAR(100) NOT NULL,
  name VARCHAR(120) NOT NULL,
  node_type VARCHAR(32) NOT NULL,
  execution_mode VARCHAR(20) NOT NULL DEFAULT 'AUTOMATIC',
  skill_binding_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
  skill_id BIGINT NULL,
  skill_version_id BIGINT NULL,
  skill_selector JSON NOT NULL,
  input_mapping JSON NOT NULL,
  output_mapping JSON NOT NULL,
  node_configuration JSON NOT NULL,
  retry_policy JSON NOT NULL,
  timeout_ms INT NULL,
  required_node BOOLEAN NOT NULL DEFAULT TRUE,
  display_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_runtime_workflow_node_version FOREIGN KEY (workflow_version_id)
    REFERENCES ai_runtime_workflow_version(id),
  CONSTRAINT fk_ai_runtime_workflow_node_skill_version FOREIGN KEY (skill_version_id, skill_id)
    REFERENCES ai_runtime_skill_version(id, skill_id),
  CONSTRAINT uk_ai_runtime_workflow_node_key UNIQUE (workflow_version_id, node_key),
  CONSTRAINT uk_ai_runtime_workflow_node_identity UNIQUE (id, workflow_version_id),
  CONSTRAINT chk_ai_runtime_workflow_node_key CHECK (
    node_key REGEXP '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'
  ),
  CONSTRAINT chk_ai_runtime_workflow_node_type CHECK (
    node_type IN ('TASK_UNDERSTANDING','WORKFLOW_RESOLUTION','SKILL_RESOLUTION','SKILL',
      'CONDITION','PARALLEL','JOIN','HUMAN_CONFIRM','DOCUMENT','TOOL','AGENT','END')
  ),
  CONSTRAINT chk_ai_runtime_workflow_node_mode CHECK (
    execution_mode IN ('AUTOMATIC','USER_CONFIRMATION','EXTERNAL_CALLBACK')
  ),
  CONSTRAINT chk_ai_runtime_workflow_node_binding CHECK (
    (skill_binding_type = 'NONE' AND skill_id IS NULL AND skill_version_id IS NULL)
    OR (skill_binding_type = 'DYNAMIC' AND skill_id IS NULL AND skill_version_id IS NULL)
    OR (skill_binding_type = 'FIXED' AND skill_id IS NOT NULL AND skill_version_id IS NOT NULL)
  ),
  CONSTRAINT chk_ai_runtime_workflow_node_json CHECK (
    JSON_TYPE(skill_selector) = 'OBJECT'
    AND JSON_TYPE(input_mapping) = 'OBJECT'
    AND JSON_TYPE(output_mapping) = 'OBJECT'
    AND JSON_TYPE(node_configuration) = 'OBJECT'
    AND JSON_TYPE(retry_policy) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_workflow_node_limits CHECK (
    (timeout_ms IS NULL OR timeout_ms BETWEEN 100 AND 86400000)
    AND display_order >= 0
  ),
  INDEX idx_ai_runtime_workflow_node_type (workflow_version_id, node_type),
  INDEX idx_ai_runtime_workflow_node_skill (skill_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_workflow_edge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_version_id BIGINT NOT NULL,
  from_node_id BIGINT NOT NULL,
  to_node_id BIGINT NOT NULL,
  edge_type VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
  condition_expression VARCHAR(1000) NULL,
  edge_configuration JSON NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_runtime_workflow_edge_version FOREIGN KEY (workflow_version_id)
    REFERENCES ai_runtime_workflow_version(id),
  CONSTRAINT fk_ai_runtime_workflow_edge_from FOREIGN KEY (from_node_id, workflow_version_id)
    REFERENCES ai_runtime_workflow_node(id, workflow_version_id),
  CONSTRAINT fk_ai_runtime_workflow_edge_to FOREIGN KEY (to_node_id, workflow_version_id)
    REFERENCES ai_runtime_workflow_node(id, workflow_version_id),
  CONSTRAINT uk_ai_runtime_workflow_edge UNIQUE (
    workflow_version_id, from_node_id, to_node_id, edge_type, priority
  ),
  CONSTRAINT chk_ai_runtime_workflow_edge_type CHECK (
    edge_type IN ('SUCCESS','FAILURE','CONDITIONAL','ALWAYS')
  ),
  CONSTRAINT chk_ai_runtime_workflow_edge_nodes CHECK (
    from_node_id <> to_node_id
  ),
  CONSTRAINT chk_ai_runtime_workflow_edge_condition CHECK (
    (edge_type = 'CONDITIONAL' AND condition_expression IS NOT NULL)
    OR (edge_type <> 'CONDITIONAL' AND condition_expression IS NULL)
  ),
  CONSTRAINT chk_ai_runtime_workflow_edge_json CHECK (
    JSON_TYPE(edge_configuration) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_workflow_edge_priority CHECK (
    priority >= 0
  ),
  INDEX idx_ai_runtime_workflow_edge_from (workflow_version_id, from_node_id, priority),
  INDEX idx_ai_runtime_workflow_edge_to (workflow_version_id, to_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ai_runtime_workflow_version
  ADD CONSTRAINT fk_ai_runtime_workflow_version_entry
  FOREIGN KEY (id, entry_node_key)
  REFERENCES ai_runtime_workflow_node(workflow_version_id, node_key);

ALTER TABLE ai_runtime_workflow
  ADD CONSTRAINT fk_ai_runtime_workflow_current_version
  FOREIGN KEY (current_version_id, id)
  REFERENCES ai_runtime_workflow_version(id, workflow_id);
