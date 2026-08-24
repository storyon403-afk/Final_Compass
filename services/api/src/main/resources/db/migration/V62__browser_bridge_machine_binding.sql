CREATE TABLE browser_bridge_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  secret_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_exchanged_at TIMESTAMP NULL,
  revoked_at TIMESTAMP NULL,
  CONSTRAINT fk_browser_bridge_binding_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT uk_browser_bridge_binding_user UNIQUE (user_id),
  CONSTRAINT uk_browser_bridge_binding_secret UNIQUE (secret_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE browser_bridge_ticket (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  binding_id BIGINT NOT NULL,
  ticket_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  consumed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_browser_bridge_ticket_binding FOREIGN KEY (binding_id) REFERENCES browser_bridge_binding(id) ON DELETE CASCADE,
  CONSTRAINT uk_browser_bridge_ticket_hash UNIQUE (ticket_hash),
  INDEX idx_browser_bridge_ticket_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
