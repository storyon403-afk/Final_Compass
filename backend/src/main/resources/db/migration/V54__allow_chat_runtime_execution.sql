ALTER TABLE ai_runtime_execution
  DROP CHECK chk_ai_runtime_execution_runtime;

ALTER TABLE ai_runtime_execution
  ADD CONSTRAINT chk_ai_runtime_execution_runtime CHECK (
    runtime_type IN ('LEGACY','WORKFLOW','CHAT','AGENT','MULTI_WEB_AGENT')
  );
