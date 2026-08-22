CREATE TABLE livedoc_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  document_kind ENUM('vdocx','vpptx') NOT NULL,
  content LONGBLOB NOT NULL,
  size_bytes BIGINT NOT NULL,
  content_digest CHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  INDEX idx_livedoc_project_user_updated (user_id,updated_at),
  CONSTRAINT fk_livedoc_project_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT chk_livedoc_project_size CHECK (size_bytes > 0 AND size_bytes <= 104857600)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
