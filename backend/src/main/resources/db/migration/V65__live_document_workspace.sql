CREATE TABLE live_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL,
  owner_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  kind ENUM('FLOW_DOCUMENT','SLIDE_DECK') NOT NULL DEFAULT 'FLOW_DOCUMENT',
  source_format VARCHAR(32) NOT NULL DEFAULT 'markdown-hybrid',
  source LONGTEXT NOT NULL,
  document_css TEXT NULL,
  islands_json JSON NOT NULL,
  permissions_json JSON NOT NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_live_document_owner FOREIGN KEY (owner_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT uk_live_document_public_id UNIQUE (public_id),
  INDEX idx_live_document_owner_updated (owner_id,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE live_document_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  revision BIGINT NOT NULL,
  actor_type ENUM('HUMAN','AGENT','SYSTEM') NOT NULL,
  actor_id VARCHAR(120) NOT NULL,
  operation_type VARCHAR(48) NOT NULL,
  operation_json JSON NOT NULL,
  source_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_live_document_revision_document FOREIGN KEY (document_id) REFERENCES live_document(id) ON DELETE CASCADE,
  CONSTRAINT uk_live_document_revision UNIQUE (document_id,revision),
  INDEX idx_live_document_revision_created (document_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
