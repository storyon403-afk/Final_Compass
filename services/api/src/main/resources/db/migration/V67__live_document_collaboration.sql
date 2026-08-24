CREATE TABLE live_document_collaborator (
  document_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role ENUM('EDITOR','VIEWER') NOT NULL DEFAULT 'EDITOR',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (document_id,user_id),
  CONSTRAINT fk_live_doc_collab_document FOREIGN KEY (document_id) REFERENCES live_document(id) ON DELETE CASCADE,
  CONSTRAINT fk_live_doc_collab_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
