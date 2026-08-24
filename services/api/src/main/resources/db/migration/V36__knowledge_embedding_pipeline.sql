ALTER TABLE knowledge_chunk
  ADD COLUMN embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER metadata,
  ADD COLUMN embedding_attempts INT NOT NULL DEFAULT 0 AFTER embedding_status,
  ADD COLUMN embedding_error_code VARCHAR(80) NULL AFTER embedding_attempts,
  ADD CONSTRAINT chk_knowledge_embedding_status CHECK(embedding_status IN ('PENDING','PROCESSING','READY','FAILED','SKIPPED'));

INSERT INTO ai_runtime_provider_model(provider_id,model_key,display_name,status,context_window,max_output_units,
  supports_structured_output,supports_tool_calling,routing_priority,routing_weight,configuration)
SELECT id,'text-embedding-3-small','OpenAI text-embedding-3-small','ACTIVE',8191,NULL,FALSE,FALSE,50,100,
  JSON_OBJECT('embeddingAdapterKey','openai-embeddings-v1','embeddingDimension',1536,'maximumBatchSize',64)
FROM ai_runtime_provider WHERE provider_key='openai'
ON DUPLICATE KEY UPDATE configuration=VALUES(configuration);

INSERT IGNORE INTO ai_runtime_provider_model_capability(provider_model_id,capability_id,configuration,verified_at)
SELECT m.id,c.id,JSON_OBJECT('source','knowledge-embedding-pipeline'),CURRENT_TIMESTAMP(6)
FROM ai_runtime_provider_model m JOIN ai_runtime_provider p ON p.id=m.provider_id
JOIN ai_runtime_capability c ON c.capability_key='EMBEDDING'
WHERE p.provider_key='openai' AND m.model_key='text-embedding-3-small';

CREATE TABLE knowledge_embedding_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_key VARCHAR(80) NOT NULL,
  source_id BIGINT NOT NULL,
  provider_key VARCHAR(64) NOT NULL,
  model_key VARCHAR(120) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  chunk_count INT NOT NULL,
  embedded_count INT NOT NULL DEFAULT 0,
  error_code VARCHAR(80) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_knowledge_embedding_job_key UNIQUE(job_key),
  CONSTRAINT fk_knowledge_embedding_job_source FOREIGN KEY(source_id) REFERENCES knowledge_source(id),
  CONSTRAINT chk_knowledge_embedding_job_status CHECK(status IN ('RUNNING','SUCCEEDED','FAILED')),
  INDEX idx_knowledge_embedding_job_source(source_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
