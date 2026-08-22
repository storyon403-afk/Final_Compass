ALTER TABLE ai_agent_definition
  ADD COLUMN approval_policy VARCHAR(32) NOT NULL DEFAULT 'REQUIRE_APPROVAL',
  ADD COLUMN capabilities JSON NULL;

UPDATE ai_agent_definition SET capabilities=JSON_ARRAY(
  'READ_DOCUMENT','EDIT_DOCUMENT','CREATE_MERMAID','CREATE_THREE','CREATE_ISLAND','CREATE_SLIDES','REQUEST_EXPORT'
) WHERE capabilities IS NULL;

-- The historical placeholder is not a published document Agent. An administrator must opt in.
UPDATE ai_agent_definition SET status='INACTIVE' WHERE agent_key='default-external-agent';

CREATE TABLE live_document_agent_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  document_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  agent_key VARCHAR(80) NOT NULL,
  run_key CHAR(36) NULL,
  goal TEXT NOT NULL,
  approval_policy VARCHAR(32) NOT NULL,
  tool_token CHAR(36) NOT NULL UNIQUE,
  status VARCHAR(32) NOT NULL DEFAULT 'STARTING',
  created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_doc_agent_session_document FOREIGN KEY(document_id) REFERENCES live_document(id) ON DELETE CASCADE,
  CONSTRAINT fk_doc_agent_session_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  INDEX idx_doc_agent_session_document(document_id,created_at)
);

CREATE TABLE live_document_agent_patch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  session_id BIGINT NOT NULL,
  base_revision BIGINT NOT NULL,
  operation_type VARCHAR(48) NOT NULL,
  operation_json JSON NOT NULL,
  summary VARCHAR(500) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  decided_by BIGINT NULL,
  decided_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_doc_agent_patch_session FOREIGN KEY(session_id) REFERENCES live_document_agent_session(id) ON DELETE CASCADE,
  INDEX idx_doc_agent_patch_session(session_id,created_at)
);
