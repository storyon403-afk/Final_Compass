INSERT INTO ai_runtime_capability(capability_key,name,description) VALUES
  ('TEXT_REASONING','文本推理','分析、判断、规划和文本生成能力'),
  ('VISION','视觉理解','理解图片及图片中文字、公式和结构的能力'),
  ('LONG_CONTEXT','长上下文','处理长文档和大上下文窗口的能力'),
  ('CODE','代码','理解、生成或分析代码的能力'),
  ('STRUCTURED_OUTPUT','结构化输出','按照结构化 Schema 返回结果的能力'),
  ('TOOL_CALLING','工具调用','产生受控工具调用请求的能力'),
  ('EMBEDDING','向量嵌入','将内容转换为向量表示的能力');

INSERT INTO ai_runtime_provider(
  provider_key,name,provider_type,adapter_key,status,credential_policy,configuration
) VALUES
  ('openai','OpenAI','API','openai-responses-v1','ACTIVE',
    JSON_OBJECT('supportedSources',JSON_ARRAY('PLATFORM','STORED_BYOK','EPHEMERAL_BYOK')),
    JSON_OBJECT('migrationSource','AiProviderConfiguration')),
  ('deepseek','DeepSeek','API','deepseek-chat-v1','ACTIVE',
    JSON_OBJECT('supportedSources',JSON_ARRAY('PLATFORM','STORED_BYOK','EPHEMERAL_BYOK')),
    JSON_OBJECT('migrationSource','AiProviderConfiguration')),
  ('gemini','Google / Gemini','API','gemini-generate-content-v1','ACTIVE',
    JSON_OBJECT('supportedSources',JSON_ARRAY('PLATFORM','STORED_BYOK','EPHEMERAL_BYOK')),
    JSON_OBJECT('migrationSource','AiProviderConfiguration'));

INSERT INTO ai_runtime_provider_endpoint(
  provider_id,endpoint_key,base_url,priority,weight,status,configuration
)
SELECT id,'default',
  CASE provider_key
    WHEN 'openai' THEN 'https://api.openai.com'
    WHEN 'deepseek' THEN 'https://api.deepseek.com'
    WHEN 'gemini' THEN 'https://generativelanguage.googleapis.com'
  END,
  100,100,'ACTIVE',JSON_OBJECT('officialEndpoint',TRUE)
FROM ai_runtime_provider
WHERE provider_key IN ('openai','deepseek','gemini');

INSERT INTO ai_runtime_provider_model(
  provider_id,model_key,display_name,status,routing_priority,routing_weight,configuration
)
SELECT p.id,c.model_name,c.model_name,
  CASE WHEN c.enabled THEN 'ACTIVE' ELSE 'DISABLED' END,
  100,100,JSON_OBJECT('migrationSource','platform_ai_config')
FROM platform_ai_config c
JOIN ai_runtime_provider p ON p.provider_key=c.provider
WHERE c.provider IN ('openai','deepseek','gemini')
  AND c.model_name REGEXP '^[A-Za-z0-9][A-Za-z0-9._:/-]{1,119}$';

INSERT INTO ai_runtime_provider_model_capability(provider_model_id,capability_id,configuration,verified_at)
SELECT m.id,c.id,JSON_OBJECT('source','legacy-adapter-capability'),CURRENT_TIMESTAMP
FROM ai_runtime_provider_model m
JOIN ai_runtime_provider p ON p.id=m.provider_id
JOIN ai_runtime_capability c ON c.capability_key='TEXT_REASONING'
WHERE p.provider_key IN ('openai','deepseek','gemini');

INSERT INTO ai_runtime_provider_model_capability(provider_model_id,capability_id,configuration,verified_at)
SELECT m.id,c.id,JSON_OBJECT('source','legacy-adapter-capability'),CURRENT_TIMESTAMP
FROM ai_runtime_provider_model m
JOIN ai_runtime_provider p ON p.id=m.provider_id
JOIN ai_runtime_capability c ON c.capability_key='VISION'
WHERE p.provider_key IN ('openai','gemini');
