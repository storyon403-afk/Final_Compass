UPDATE ai_runtime_provider_model m JOIN ai_runtime_provider p ON p.id=m.provider_id
SET m.context_window=COALESCE(m.context_window,32768),m.max_output_units=COALESCE(m.max_output_units,4096)
WHERE p.provider_key IN ('openai','deepseek','gemini') AND m.status='ACTIVE' AND m.model_key<>'text-embedding-3-small';
