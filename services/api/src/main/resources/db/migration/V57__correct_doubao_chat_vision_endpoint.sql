-- V56 已进入 Flyway 历史，豆包的真实协议和推理接入点通过新迁移修正，禁止回改历史迁移。
UPDATE ai_runtime_provider
SET adapter_key='openai-chat-compatible-v1',
    configuration=JSON_OBJECT('protocol','openai-chat-completions')
WHERE provider_key='doubao';

UPDATE ai_runtime_provider_model model
JOIN ai_runtime_provider provider ON provider.id=model.provider_id
SET model.model_key='doubao-seed-2-1-turbo-260628',
    model.display_name='Doubao Seed 2.1 Turbo',
    model.configuration=JSON_OBJECT('source','user-endpoint')
WHERE provider.provider_key='doubao'
  AND model.model_key='Doubao-Seed-2.1-turbo';
