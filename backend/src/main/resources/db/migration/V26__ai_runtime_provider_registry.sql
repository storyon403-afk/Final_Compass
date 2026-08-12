CREATE TABLE ai_runtime_provider (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_key VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  provider_type VARCHAR(20) NOT NULL,
  adapter_key VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  credential_policy JSON NOT NULL,
  configuration JSON NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_runtime_provider_key UNIQUE (provider_key),
  CONSTRAINT chk_ai_runtime_provider_key CHECK (
    provider_key REGEXP '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'
  ),
  CONSTRAINT chk_ai_runtime_provider_type CHECK (
    provider_type IN ('API','BROWSER','LOCAL')
  ),
  CONSTRAINT chk_ai_runtime_provider_status CHECK (
    status IN ('ACTIVE','DEGRADED','DISABLED')
  ),
  CONSTRAINT chk_ai_runtime_provider_json CHECK (
    JSON_TYPE(credential_policy) = 'OBJECT'
    AND JSON_TYPE(configuration) = 'OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_provider_browser_adapter CHECK (
    provider_type <> 'BROWSER' OR adapter_key = 'browser-agent-gateway-v1'
  ),
  INDEX idx_ai_runtime_provider_status_type (status, provider_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_provider_endpoint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_id BIGINT NOT NULL,
  endpoint_key VARCHAR(64) NOT NULL,
  base_url VARCHAR(500) NOT NULL,
  region VARCHAR(40) NULL,
  priority INT NOT NULL DEFAULT 100,
  weight INT NOT NULL DEFAULT 100,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  connect_timeout_ms INT NOT NULL DEFAULT 8000,
  request_timeout_ms INT NOT NULL DEFAULT 60000,
  health_check_path VARCHAR(200) NULL,
  configuration JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_runtime_provider_endpoint_provider FOREIGN KEY (provider_id)
    REFERENCES ai_runtime_provider(id),
  CONSTRAINT uk_ai_runtime_provider_endpoint UNIQUE (provider_id, endpoint_key),
  CONSTRAINT chk_ai_runtime_provider_endpoint_key CHECK (
    endpoint_key REGEXP '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'
  ),
  CONSTRAINT chk_ai_runtime_provider_endpoint_status CHECK (
    status IN ('ACTIVE','DEGRADED','DISABLED')
  ),
  CONSTRAINT chk_ai_runtime_provider_endpoint_routing CHECK (
    priority BETWEEN 0 AND 100000 AND weight BETWEEN 0 AND 10000
  ),
  CONSTRAINT chk_ai_runtime_provider_endpoint_timeouts CHECK (
    connect_timeout_ms BETWEEN 100 AND 120000
    AND request_timeout_ms BETWEEN connect_timeout_ms AND 3600000
  ),
  CONSTRAINT chk_ai_runtime_provider_endpoint_health_path CHECK (
    health_check_path IS NULL OR health_check_path REGEXP '^/[A-Za-z0-9._~!$&''()*+,;=:@%/-]*$'
  ),
  CONSTRAINT chk_ai_runtime_provider_endpoint_configuration CHECK (
    JSON_TYPE(configuration) = 'OBJECT'
  ),
  INDEX idx_ai_runtime_provider_endpoint_routing (provider_id, status, priority, weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_provider_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_id BIGINT NOT NULL,
  model_key VARCHAR(120) NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  context_window INT NULL,
  max_output_units INT NULL,
  supports_structured_output BOOLEAN NOT NULL DEFAULT FALSE,
  supports_tool_calling BOOLEAN NOT NULL DEFAULT FALSE,
  input_unit_price DECIMAL(18,8) NULL,
  output_unit_price DECIMAL(18,8) NULL,
  currency CHAR(3) NULL,
  routing_priority INT NOT NULL DEFAULT 100,
  routing_weight INT NOT NULL DEFAULT 100,
  configuration JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_runtime_provider_model_provider FOREIGN KEY (provider_id)
    REFERENCES ai_runtime_provider(id),
  CONSTRAINT uk_ai_runtime_provider_model UNIQUE (provider_id, model_key),
  CONSTRAINT uk_ai_runtime_provider_model_identity UNIQUE (id, provider_id),
  CONSTRAINT chk_ai_runtime_provider_model_key CHECK (
    model_key REGEXP '^[A-Za-z0-9][A-Za-z0-9._:/-]{1,119}$'
  ),
  CONSTRAINT chk_ai_runtime_provider_model_status CHECK (
    status IN ('ACTIVE','DEPRECATED','DISABLED')
  ),
  CONSTRAINT chk_ai_runtime_provider_model_limits CHECK (
    (context_window IS NULL OR context_window > 0)
    AND (max_output_units IS NULL OR max_output_units > 0)
  ),
  CONSTRAINT chk_ai_runtime_provider_model_prices CHECK (
    (input_unit_price IS NULL OR input_unit_price >= 0)
    AND (output_unit_price IS NULL OR output_unit_price >= 0)
    AND ((input_unit_price IS NULL AND output_unit_price IS NULL AND currency IS NULL)
      OR ((input_unit_price IS NOT NULL OR output_unit_price IS NOT NULL)
        AND currency REGEXP '^[A-Z]{3}$'))
  ),
  CONSTRAINT chk_ai_runtime_provider_model_routing CHECK (
    routing_priority BETWEEN 0 AND 100000 AND routing_weight BETWEEN 0 AND 10000
  ),
  CONSTRAINT chk_ai_runtime_provider_model_configuration CHECK (
    JSON_TYPE(configuration) = 'OBJECT'
  ),
  INDEX idx_ai_runtime_provider_model_routing (provider_id, status, routing_priority, routing_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_capability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  capability_key VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_runtime_capability_key UNIQUE (capability_key),
  CONSTRAINT chk_ai_runtime_capability_key CHECK (
    capability_key REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
  ),
  CONSTRAINT chk_ai_runtime_capability_status CHECK (
    status IN ('ACTIVE','DEPRECATED')
  ),
  INDEX idx_ai_runtime_capability_status (status, capability_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_provider_model_capability (
  provider_model_id BIGINT NOT NULL,
  capability_id BIGINT NOT NULL,
  configuration JSON NOT NULL,
  verified_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (provider_model_id, capability_id),
  CONSTRAINT fk_ai_runtime_model_capability_model FOREIGN KEY (provider_model_id)
    REFERENCES ai_runtime_provider_model(id),
  CONSTRAINT fk_ai_runtime_model_capability_capability FOREIGN KEY (capability_id)
    REFERENCES ai_runtime_capability(id),
  CONSTRAINT chk_ai_runtime_model_capability_configuration CHECK (
    JSON_TYPE(configuration) = 'OBJECT'
  ),
  INDEX idx_ai_runtime_model_capability_lookup (capability_id, provider_model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
