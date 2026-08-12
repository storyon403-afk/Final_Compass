UPDATE ai_runtime_provider_model m JOIN ai_runtime_provider p ON p.id=m.provider_id
SET m.supports_structured_output=TRUE
WHERE p.provider_key IN ('openai','deepseek','gemini') AND m.status='ACTIVE' AND m.model_key<>'text-embedding-3-small';

INSERT IGNORE INTO ai_runtime_provider_model_capability(provider_model_id,capability_id,configuration,verified_at)
SELECT m.id,c.id,JSON_OBJECT('source','legacy-adapter-json-mode'),CURRENT_TIMESTAMP
FROM ai_runtime_provider_model m JOIN ai_runtime_provider p ON p.id=m.provider_id
JOIN ai_runtime_capability c ON c.capability_key='STRUCTURED_OUTPUT'
WHERE p.provider_key IN ('openai','deepseek','gemini') AND m.status='ACTIVE' AND m.model_key<>'text-embedding-3-small';

ALTER TABLE ai_center_content_page
  ADD COLUMN content_format VARCHAR(16) NOT NULL DEFAULT 'HTML' AFTER subtitle,
  ADD COLUMN content_body MEDIUMTEXT NULL AFTER content_format;
UPDATE ai_center_content_page SET content_body=content_html WHERE content_body IS NULL;
ALTER TABLE ai_center_content_page MODIFY content_body MEDIUMTEXT NOT NULL;
