CREATE TABLE ai_evolution_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_key VARCHAR(64) NOT NULL,
  metric_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  trigger_type VARCHAR(20) NOT NULL,
  triggered_by BIGINT NULL,
  error_summary VARCHAR(500) NULL,
  started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_ai_evolution_run_key UNIQUE(run_key),
  CONSTRAINT chk_ai_evolution_run_status CHECK(status IN ('RUNNING','SUCCEEDED','FAILED')),
  CONSTRAINT chk_ai_evolution_run_trigger CHECK(trigger_type IN ('MANUAL','SCHEDULED')),
  INDEX idx_ai_evolution_run_date(metric_date,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_skill_daily_metric (
  metric_date DATE NOT NULL,
  skill_id BIGINT NOT NULL,
  skill_version_id BIGINT NOT NULL,
  skill_key_snapshot VARCHAR(100) NOT NULL,
  skill_version_snapshot VARCHAR(32) NOT NULL,
  execution_count BIGINT NOT NULL,
  succeeded_count BIGINT NOT NULL,
  failed_count BIGINT NOT NULL,
  feedback_count BIGINT NOT NULL,
  negative_feedback_count BIGINT NOT NULL,
  input_units BIGINT NOT NULL,
  output_units BIGINT NOT NULL,
  estimated_cost DECIMAL(18,8) NOT NULL,
  average_latency_ms DECIMAL(18,3) NULL,
  PRIMARY KEY(metric_date,skill_version_id),
  CONSTRAINT fk_ai_skill_metric_version FOREIGN KEY(skill_version_id,skill_id) REFERENCES ai_runtime_skill_version(id,skill_id),
  INDEX idx_ai_skill_metric_quality(skill_version_id,metric_date,negative_feedback_count,failed_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_provider_daily_metric (
  metric_date DATE NOT NULL,
  provider_id BIGINT NOT NULL,
  provider_model_id BIGINT NOT NULL,
  provider_key_snapshot VARCHAR(64) NOT NULL,
  model_key_snapshot VARCHAR(120) NOT NULL,
  invocation_count BIGINT NOT NULL,
  succeeded_count BIGINT NOT NULL,
  failed_count BIGINT NOT NULL,
  timeout_count BIGINT NOT NULL,
  input_units BIGINT NOT NULL,
  output_units BIGINT NOT NULL,
  estimated_cost DECIMAL(18,8) NOT NULL,
  average_latency_ms DECIMAL(18,3) NULL,
  PRIMARY KEY(metric_date,provider_model_id),
  CONSTRAINT fk_ai_provider_metric_model FOREIGN KEY(provider_model_id,provider_id) REFERENCES ai_runtime_provider_model(id,provider_id),
  INDEX idx_ai_provider_metric_quality(provider_model_id,metric_date,failed_count,timeout_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_workflow_daily_metric (
  metric_date DATE NOT NULL,
  workflow_key VARCHAR(100) NOT NULL,
  workflow_version VARCHAR(32) NOT NULL,
  execution_count BIGINT NOT NULL,
  succeeded_count BIGINT NOT NULL,
  failed_count BIGINT NOT NULL,
  cancelled_count BIGINT NOT NULL,
  average_duration_ms DECIMAL(18,3) NULL,
  PRIMARY KEY(metric_date,workflow_key,workflow_version),
  INDEX idx_ai_workflow_metric_quality(workflow_key,metric_date,failed_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_workflow_node_daily_metric (
  metric_date DATE NOT NULL,
  workflow_key VARCHAR(100) NOT NULL,
  workflow_version VARCHAR(32) NOT NULL,
  node_key VARCHAR(100) NOT NULL,
  node_type VARCHAR(32) NOT NULL,
  execution_count BIGINT NOT NULL,
  succeeded_count BIGINT NOT NULL,
  failed_count BIGINT NOT NULL,
  average_latency_ms DECIMAL(18,3) NULL,
  PRIMARY KEY(metric_date,workflow_key,workflow_version,node_key),
  INDEX idx_ai_workflow_node_metric_failure(metric_date,failed_count,node_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_skill_optimization_recommendation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  recommendation_key VARCHAR(64) NOT NULL,
  skill_id BIGINT NOT NULL,
  skill_version_id BIGINT NOT NULL,
  recommendation_type VARCHAR(40) NOT NULL,
  window_start DATE NOT NULL,
  window_end DATE NOT NULL,
  evidence JSON NOT NULL,
  recommendation VARCHAR(2000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  reviewed_by BIGINT NULL,
  review_note VARCHAR(2000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  reviewed_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_ai_skill_recommendation_key UNIQUE(recommendation_key),
  CONSTRAINT uk_ai_skill_recommendation_window UNIQUE(skill_version_id,window_start,window_end,recommendation_type),
  CONSTRAINT fk_ai_skill_recommendation_version FOREIGN KEY(skill_version_id,skill_id) REFERENCES ai_runtime_skill_version(id,skill_id),
  CONSTRAINT chk_ai_skill_recommendation_type CHECK(recommendation_type IN ('NEGATIVE_FEEDBACK','EXECUTION_FAILURE','LATENCY_COST')),
  CONSTRAINT chk_ai_skill_recommendation_status CHECK(status IN ('DRAFT','APPROVED','REJECTED','APPLIED')),
  CONSTRAINT chk_ai_skill_recommendation_evidence CHECK(JSON_TYPE(evidence)='OBJECT'),
  INDEX idx_ai_skill_recommendation_status(status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
