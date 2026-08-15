ALTER TABLE ai_runtime_run
  ADD COLUMN http_trace_id VARCHAR(12) NULL AFTER run_key,
  ADD INDEX idx_runtime_run_http_trace (http_trace_id, created_at);
