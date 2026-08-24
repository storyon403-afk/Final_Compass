CREATE TABLE knowledge_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_key VARCHAR(80) NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  external_reference VARCHAR(255) NULL,
  title VARCHAR(255) NOT NULL,
  scope_type VARCHAR(20) NOT NULL,
  scope_key VARCHAR(120) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  submitted_by BIGINT NOT NULL,
  approved_by BIGINT NULL,
  approved_at TIMESTAMP(6) NULL,
  content_digest CHAR(64) NULL,
  error_code VARCHAR(80) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT uk_knowledge_source_key UNIQUE(source_key),
  CONSTRAINT chk_knowledge_source_type CHECK(source_type IN ('UPLOAD','TEACHER_PROFILE','FORUM','GUIDE','ADMIN')),
  CONSTRAINT chk_knowledge_scope_type CHECK(scope_type IN ('PUBLIC','USER','COURSE')),
  CONSTRAINT chk_knowledge_source_status CHECK(status IN ('PENDING','APPROVED','PROCESSING','READY','FAILED','REVOKED')),
  INDEX idx_knowledge_source_scope(scope_type,scope_key,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  version INT NOT NULL,
  markdown MEDIUMTEXT NOT NULL,
  metadata JSON NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_knowledge_document_source FOREIGN KEY(source_id) REFERENCES knowledge_source(id),
  CONSTRAINT uk_knowledge_document_version UNIQUE(source_id,version),
  CONSTRAINT chk_knowledge_document_metadata CHECK(JSON_TYPE(metadata)='OBJECT'),
  INDEX idx_knowledge_document_active(source_id,active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  heading VARCHAR(500) NULL,
  content TEXT NOT NULL,
  character_start INT NOT NULL,
  character_end INT NOT NULL,
  token_estimate INT NOT NULL,
  metadata JSON NOT NULL,
  embedding_model VARCHAR(120) NULL,
  embedding_dimension INT NULL,
  embedding BLOB NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_knowledge_chunk_document FOREIGN KEY(document_id) REFERENCES knowledge_document(id),
  CONSTRAINT uk_knowledge_chunk_order UNIQUE(document_id,chunk_index),
  CONSTRAINT chk_knowledge_chunk_range CHECK(character_start>=0 AND character_end>character_start),
  CONSTRAINT chk_knowledge_chunk_embedding CHECK((embedding IS NULL AND embedding_model IS NULL AND embedding_dimension IS NULL) OR (embedding IS NOT NULL AND embedding_model IS NOT NULL AND embedding_dimension>0)),
  CONSTRAINT chk_knowledge_chunk_metadata CHECK(JSON_TYPE(metadata)='OBJECT'),
  FULLTEXT INDEX ftx_knowledge_chunk_content(content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO ai_runtime_tool(tool_key,name,description,status,risk_level) VALUES
 ('Knowledge.search','知识库检索','仅通过 Knowledge Service 检索审核并完成摄取的知识内容。','ACTIVE','MEDIUM');
SET @knowledge_tool_id=LAST_INSERT_ID();
INSERT INTO ai_runtime_tool_version(tool_id,version,lifecycle_status,transport_type,executor_key,input_schema,output_schema,permission_policy,configuration,timeout_ms,max_result_bytes,checksum,published_at)
VALUES(@knowledge_tool_id,'1.0.0','PUBLISHED','INTERNAL','knowledge-search-v1',
 JSON_OBJECT('type','object','properties',JSON_OBJECT('query',JSON_OBJECT('type','string'),'limit',JSON_OBJECT('type','integer')),'required',JSON_ARRAY('query'),'additionalProperties',FALSE),
 JSON_OBJECT('type','object','properties',JSON_OBJECT('results',JSON_OBJECT('type','array','items',JSON_OBJECT('type','object'))),'required',JSON_ARRAY('results'),'additionalProperties',FALSE),
 JSON_OBJECT('requiredPermissions',JSON_ARRAY('KNOWLEDGE_READ')),JSON_OBJECT('service','KnowledgeService'),10000,524288,SHA2('Knowledge.search:1.0.0',256),CURRENT_TIMESTAMP(6));
UPDATE ai_runtime_tool SET current_version_id=LAST_INSERT_ID() WHERE id=@knowledge_tool_id;
