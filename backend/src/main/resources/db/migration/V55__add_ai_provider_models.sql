-- OpenAI Chat Completions 兼容协议只注册一次；Provider 差异保留在数据库定义中。
-- 如果本迁移已经在共享环境执行过，请不要修改历史文件，应将本文件内容放入新的迁移版本。
UPDATE ai_runtime_provider
SET adapter_key='openai-chat-compatible-v1'
WHERE provider_key='deepseek';

INSERT INTO ai_runtime_provider(
  provider_key,name,provider_type,adapter_key,status,credential_policy,configuration
) VALUES
  ('kimi','Kimi / Moonshot','API','openai-chat-compatible-v1','ACTIVE',
   JSON_OBJECT('supportedSources',JSON_ARRAY('PLATFORM','STORED_BYOK','EPHEMERAL_BYOK')),
   JSON_OBJECT('protocol','openai-chat-completions')),
  ('qwen','Qwen / DashScope','API','openai-chat-compatible-v1','ACTIVE',
   JSON_OBJECT('supportedSources',JSON_ARRAY('PLATFORM','STORED_BYOK','EPHEMERAL_BYOK')),
   JSON_OBJECT('protocol','openai-chat-completions'));

INSERT INTO ai_runtime_provider_endpoint(
  provider_id,endpoint_key,base_url,region,priority,weight,status,configuration
)
SELECT id,'default','https://api.moonshot.cn/v1',NULL,100,100,'ACTIVE',
       JSON_OBJECT('officialEndpoint',TRUE)
FROM ai_runtime_provider WHERE provider_key='kimi'
UNION ALL
SELECT id,'default','https://dashscope.aliyuncs.com/compatible-mode/v1','cn',100,100,'ACTIVE',
       JSON_OBJECT('officialEndpoint',TRUE)
FROM ai_runtime_provider WHERE provider_key='qwen';

-- model_key 必须是供应商 API 实际接受的 ID；展示名称与调用 ID 分开维护。
INSERT INTO ai_runtime_provider_model(
  provider_id,model_key,display_name,status,context_window,max_output_units,
  supports_structured_output,supports_tool_calling,routing_priority,routing_weight,configuration
)
SELECT id,'deepseek-v4-pro','DeepSeek V4 Pro','ACTIVE',131072,8192,
       FALSE,FALSE,150,100,JSON_OBJECT('source','manual')
FROM ai_runtime_provider WHERE provider_key='deepseek'
UNION ALL
SELECT id,'kimi-k3','Kimi K3','ACTIVE',131072,8192,
       FALSE,FALSE,140,100,JSON_OBJECT('source','manual')
FROM ai_runtime_provider WHERE provider_key='kimi'
UNION ALL
SELECT id,'qwen3.8-max','Qwen 3.8 Max','ACTIVE',131072,8192,
       FALSE,FALSE,140,100,JSON_OBJECT('source','manual')
FROM ai_runtime_provider WHERE provider_key='qwen';

-- 这里只声明当前客户端已经实现并验证的能力，避免路由到尚未支持的图片、工具或结构化输出路径。
INSERT INTO ai_runtime_provider_model_capability(
  provider_model_id,capability_id,configuration,verified_at
)
SELECT model.id,capability.id,JSON_OBJECT('source','manual'),CURRENT_TIMESTAMP
FROM ai_runtime_provider_model model
JOIN ai_runtime_provider provider ON provider.id=model.provider_id
JOIN ai_runtime_capability capability ON capability.capability_key='TEXT_REASONING'
WHERE (provider.provider_key='deepseek' AND model.model_key='deepseek-v4-pro')
   OR (provider.provider_key='kimi' AND model.model_key='kimi-k3')
   OR (provider.provider_key='qwen' AND model.model_key='qwen3.8-max');
