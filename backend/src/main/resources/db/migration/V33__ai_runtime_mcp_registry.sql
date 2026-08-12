CREATE TABLE ai_runtime_mcp_server (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  server_key VARCHAR(100) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  transport_type VARCHAR(24) NOT NULL,
  endpoint_uri VARCHAR(1000) NULL,
  protocol_version VARCHAR(20) NOT NULL DEFAULT '2025-06-18',
  auth_mode VARCHAR(24) NOT NULL DEFAULT 'NONE',
  credential_reference VARCHAR(255) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
  health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  outbound_policy JSON NOT NULL,
  configuration JSON NOT NULL,
  last_discovered_at TIMESTAMP(6) NULL,
  last_health_check_at TIMESTAMP(6) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_ai_runtime_mcp_server_key UNIQUE (server_key),
  CONSTRAINT chk_ai_runtime_mcp_server_key CHECK (
    server_key REGEXP '^[a-z][a-z0-9]*(-[a-z0-9]+)*$'
  ),
  CONSTRAINT chk_ai_runtime_mcp_transport CHECK (transport_type IN ('STREAMABLE_HTTP','STDIO')),
  CONSTRAINT chk_ai_runtime_mcp_auth CHECK (
    auth_mode IN ('NONE','PLATFORM_OAUTH','USER_OAUTH','SERVICE_TOKEN')
  ),
  CONSTRAINT chk_ai_runtime_mcp_status CHECK (status IN ('ACTIVE','DISABLED','DEPRECATED')),
  CONSTRAINT chk_ai_runtime_mcp_health CHECK (health_status IN ('UNKNOWN','HEALTHY','DEGRADED','UNHEALTHY')),
  CONSTRAINT chk_ai_runtime_mcp_json CHECK (
    JSON_TYPE(outbound_policy)='OBJECT' AND JSON_TYPE(configuration)='OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_mcp_endpoint CHECK (
    (transport_type='STREAMABLE_HTTP' AND endpoint_uri IS NOT NULL)
    OR (transport_type='STDIO' AND endpoint_uri IS NULL)
  ),
  CONSTRAINT chk_ai_runtime_mcp_credential_reference CHECK (
    (auth_mode='NONE' AND credential_reference IS NULL)
    OR (auth_mode<>'NONE' AND credential_reference IS NOT NULL)
  ),
  INDEX idx_ai_runtime_mcp_server_routing (status,health_status,transport_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_mcp_discovery_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  server_id BIGINT NOT NULL,
  discovery_id CHAR(36) NOT NULL,
  protocol_version VARCHAR(20) NOT NULL,
  server_capabilities JSON NOT NULL,
  tool_count INT NOT NULL,
  schema_digest CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  discovered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_ai_runtime_mcp_discovery_server FOREIGN KEY (server_id)
    REFERENCES ai_runtime_mcp_server(id),
  CONSTRAINT uk_ai_runtime_mcp_discovery_id UNIQUE (discovery_id),
  CONSTRAINT chk_ai_runtime_mcp_discovery_json CHECK (JSON_TYPE(server_capabilities)='OBJECT'),
  CONSTRAINT chk_ai_runtime_mcp_discovery_digest CHECK (schema_digest REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT chk_ai_runtime_mcp_discovery_status CHECK (status IN ('CURRENT','SUPERSEDED','REJECTED')),
  CONSTRAINT chk_ai_runtime_mcp_discovery_count CHECK (tool_count>=0),
  INDEX idx_ai_runtime_mcp_discovery_current (server_id,status,discovered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_mcp_discovered_tool (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  discovery_snapshot_id BIGINT NOT NULL,
  remote_tool_name VARCHAR(160) NOT NULL,
  title VARCHAR(200) NULL,
  description VARCHAR(2000) NOT NULL,
  input_schema JSON NOT NULL,
  output_schema JSON NULL,
  annotations JSON NOT NULL,
  schema_digest CHAR(64) NOT NULL,
  CONSTRAINT fk_ai_runtime_mcp_tool_snapshot FOREIGN KEY (discovery_snapshot_id)
    REFERENCES ai_runtime_mcp_discovery_snapshot(id),
  CONSTRAINT uk_ai_runtime_mcp_discovered_tool UNIQUE (discovery_snapshot_id,remote_tool_name),
  CONSTRAINT chk_ai_runtime_mcp_discovered_tool_json CHECK (
    JSON_TYPE(input_schema)='OBJECT'
    AND (output_schema IS NULL OR JSON_TYPE(output_schema)='OBJECT')
    AND JSON_TYPE(annotations)='OBJECT'
  ),
  CONSTRAINT chk_ai_runtime_mcp_discovered_tool_digest CHECK (schema_digest REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_mcp_tool_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tool_version_id BIGINT NOT NULL,
  server_id BIGINT NOT NULL,
  remote_tool_name VARCHAR(160) NOT NULL,
  pinned_schema_digest CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_runtime_mcp_binding_tool_version FOREIGN KEY (tool_version_id)
    REFERENCES ai_runtime_tool_version(id),
  CONSTRAINT fk_ai_runtime_mcp_binding_server FOREIGN KEY (server_id)
    REFERENCES ai_runtime_mcp_server(id),
  CONSTRAINT uk_ai_runtime_mcp_binding_tool_version UNIQUE (tool_version_id),
  CONSTRAINT chk_ai_runtime_mcp_binding_digest CHECK (pinned_schema_digest REGEXP '^[0-9a-f]{64}$'),
  CONSTRAINT chk_ai_runtime_mcp_binding_status CHECK (status IN ('ACTIVE','DISABLED','STALE')),
  INDEX idx_ai_runtime_mcp_binding_server (server_id,status,remote_tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
