CREATE TABLE ai_feature_setting (
  id TINYINT PRIMARY KEY,
  user_vision_auxiliary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  user_vision_ephemeral_key_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  user_vision_stored_key_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  default_vision_provider VARCHAR(40) NOT NULL DEFAULT 'gemini',
  updated_by BIGINT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_ai_feature_singleton CHECK(id=1),
  FOREIGN KEY(updated_by) REFERENCES app_user(id)
);
INSERT INTO ai_feature_setting(id) VALUES(1);

CREATE TABLE user_ai_vision_secret (
  user_id BIGINT NOT NULL,
  provider VARCHAR(40) NOT NULL,
  encrypted_key TEXT NOT NULL,
  encryption_iv VARCHAR(255) NOT NULL,
  key_fingerprint VARCHAR(80) NOT NULL,
  key_label VARCHAR(80),
  consent_version VARCHAR(40) NOT NULL,
  consented_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY(user_id,provider),
  FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE TABLE system_module_setting (
  module_key VARCHAR(40) PRIMARY KEY,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  maintenance_title VARCHAR(200) NOT NULL,
  maintenance_content MEDIUMTEXT NOT NULL,
  estimated_recovery_at TIMESTAMP(6) NULL,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_system_module_status CHECK(status IN ('OPEN','MAINTENANCE')),
  FOREIGN KEY(updated_by) REFERENCES app_user(id)
);
INSERT INTO system_module_setting(module_key,status,maintenance_title,maintenance_content) VALUES
('COURSE_NAVIGATION','OPEN','课程导航维护中','课程导航正在维护，请稍后再试。'),
('AI_CENTER','OPEN','AI Center 维护中','AI Center 正在升级模型服务，请稍后再试。'),
('CET_PRACTICE','OPEN','CET 练习维护中','CET 练习正在更新题库，请稍后再试。');

-- 豆包使用火山方舟 Responses 兼容协议；实际部署可将 model_key 改成控制台的 Endpoint ID。
INSERT INTO ai_runtime_provider(provider_key,name,provider_type,adapter_key,status,credential_policy,configuration)
VALUES('doubao','Doubao / Volcengine','API','openai-responses-v1','ACTIVE',
JSON_OBJECT('supportedSources',JSON_ARRAY('STORED_BYOK','EPHEMERAL_BYOK')),
JSON_OBJECT('protocol','openai-responses'));
INSERT INTO ai_runtime_provider_endpoint(provider_id,endpoint_key,base_url,region,priority,weight,status,configuration)
SELECT id,'default','https://ark.cn-beijing.volces.com/api/v3','cn',100,100,'ACTIVE',JSON_OBJECT('officialEndpoint',TRUE)
FROM ai_runtime_provider WHERE provider_key='doubao';
INSERT INTO ai_runtime_provider_model(provider_id,model_key,display_name,status,context_window,max_output_units,supports_structured_output,supports_tool_calling,routing_priority,routing_weight,configuration)
SELECT id,'Doubao-Seed-2.1-turbo','Doubao Seed 2.1 Turbo','ACTIVE',262144,8192,FALSE,FALSE,100,100,JSON_OBJECT('source','manual')
FROM ai_runtime_provider WHERE provider_key='doubao';
INSERT INTO ai_runtime_provider_model_capability(provider_model_id,capability_id,configuration,verified_at)
SELECT m.id,c.id,JSON_OBJECT('source','manual'),CURRENT_TIMESTAMP
FROM ai_runtime_provider_model m JOIN ai_runtime_provider p ON p.id=m.provider_id
JOIN ai_runtime_capability c ON c.capability_key IN('TEXT_REASONING','VISION')
WHERE p.provider_key='doubao' AND m.model_key='Doubao-Seed-2.1-turbo';
