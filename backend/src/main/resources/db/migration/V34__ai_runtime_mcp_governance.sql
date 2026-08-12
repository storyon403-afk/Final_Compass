ALTER TABLE ai_runtime_mcp_server
  ADD COLUMN oauth_authorization_endpoint VARCHAR(1000) NULL AFTER credential_reference,
  ADD COLUMN oauth_token_endpoint VARCHAR(1000) NULL AFTER oauth_authorization_endpoint,
  ADD COLUMN oauth_client_id VARCHAR(255) NULL AFTER oauth_token_endpoint,
  ADD COLUMN oauth_scopes VARCHAR(1000) NULL AFTER oauth_client_id,
  ADD COLUMN stdio_command JSON NULL AFTER oauth_scopes,
  ADD COLUMN stdio_working_directory VARCHAR(1000) NULL AFTER stdio_command,
  ADD CONSTRAINT chk_ai_runtime_mcp_oauth_config CHECK (
    (auth_mode NOT IN ('PLATFORM_OAUTH','USER_OAUTH'))
    OR (oauth_authorization_endpoint IS NOT NULL AND oauth_token_endpoint IS NOT NULL
      AND oauth_client_id IS NOT NULL AND oauth_scopes IS NOT NULL)
  ),
  ADD CONSTRAINT chk_ai_runtime_mcp_stdio_config CHECK (
    (transport_type<>'STDIO' AND stdio_command IS NULL AND stdio_working_directory IS NULL)
    OR (transport_type='STDIO' AND JSON_TYPE(stdio_command)='ARRAY')
  );

CREATE TABLE ai_runtime_mcp_oauth_connection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  server_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL DEFAULT 0,
  encrypted_access_token TEXT NOT NULL,
  access_token_iv VARCHAR(64) NOT NULL,
  encrypted_refresh_token TEXT NULL,
  refresh_token_iv VARCHAR(64) NULL,
  token_fingerprint VARCHAR(16) NOT NULL,
  granted_scopes VARCHAR(1000) NOT NULL,
  expires_at TIMESTAMP(6) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'CONNECTED',
  connected_by BIGINT NOT NULL,
  connected_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  refreshed_at TIMESTAMP(6) NULL,
  last_error_code VARCHAR(80) NULL,
  CONSTRAINT fk_ai_runtime_mcp_oauth_server FOREIGN KEY (server_id) REFERENCES ai_runtime_mcp_server(id),
  CONSTRAINT uk_ai_runtime_mcp_oauth_subject UNIQUE (server_id,user_id),
  CONSTRAINT chk_ai_runtime_mcp_oauth_status CHECK (status IN ('CONNECTED','EXPIRED','REVOKED','ERROR')),
  CONSTRAINT chk_ai_runtime_mcp_oauth_refresh_pair CHECK (
    (encrypted_refresh_token IS NULL AND refresh_token_iv IS NULL)
    OR (encrypted_refresh_token IS NOT NULL AND refresh_token_iv IS NOT NULL)
  ),
  INDEX idx_ai_runtime_mcp_oauth_status (status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_runtime_mcp_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  server_id BIGINT NOT NULL,
  discovery_snapshot_id BIGINT NOT NULL,
  discovered_tool_id BIGINT NOT NULL,
  decision VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  required_permissions JSON NOT NULL,
  target_tool_key VARCHAR(120) NOT NULL,
  target_version VARCHAR(32) NOT NULL,
  requested_by BIGINT NOT NULL,
  reviewed_by BIGINT NULL,
  review_note VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  reviewed_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_ai_runtime_mcp_approval_server FOREIGN KEY (server_id) REFERENCES ai_runtime_mcp_server(id),
  CONSTRAINT fk_ai_runtime_mcp_approval_snapshot FOREIGN KEY (discovery_snapshot_id)
    REFERENCES ai_runtime_mcp_discovery_snapshot(id),
  CONSTRAINT fk_ai_runtime_mcp_approval_tool FOREIGN KEY (discovered_tool_id)
    REFERENCES ai_runtime_mcp_discovered_tool(id),
  CONSTRAINT uk_ai_runtime_mcp_approval_tool UNIQUE (discovered_tool_id,target_tool_key,target_version),
  CONSTRAINT chk_ai_runtime_mcp_approval_decision CHECK (decision IN ('PENDING','APPROVED','REJECTED')),
  CONSTRAINT chk_ai_runtime_mcp_approval_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT chk_ai_runtime_mcp_approval_permissions CHECK (JSON_TYPE(required_permissions)='ARRAY'),
  INDEX idx_ai_runtime_mcp_approval_queue (decision,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
