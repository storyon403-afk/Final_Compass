CREATE TABLE ai_feedback_prompt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt_key VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  execution_id BIGINT NULL,
  document_job_id BIGINT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  sample_rate DECIMAL(5,4) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OFFERED',
  offered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  resolved_at TIMESTAMP(6) NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_ai_feedback_prompt_key UNIQUE(prompt_key),
  CONSTRAINT fk_ai_feedback_prompt_execution FOREIGN KEY(execution_id) REFERENCES ai_runtime_execution(id),
  CONSTRAINT fk_ai_feedback_prompt_document FOREIGN KEY(document_job_id) REFERENCES document_generation_job(id),
  CONSTRAINT chk_ai_feedback_prompt_context CHECK(execution_id IS NOT NULL OR document_job_id IS NOT NULL),
  CONSTRAINT chk_ai_feedback_prompt_trigger CHECK(trigger_type IN ('NEXT_REQUEST','TASK_COMPLETION','FILE_GENERATION','MULTI_STEP_TASK','SIMPLE_ANSWER')),
  CONSTRAINT chk_ai_feedback_prompt_rate CHECK(sample_rate BETWEEN 0 AND 1),
  CONSTRAINT chk_ai_feedback_prompt_status CHECK(status IN ('OFFERED','SUBMITTED','DISMISSED','EXPIRED')),
  INDEX idx_ai_feedback_prompt_user(user_id,status,offered_at),
  INDEX idx_ai_feedback_prompt_context(execution_id,document_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_task_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  execution_id BIGINT NULL,
  document_job_id BIGINT NULL,
  template_id BIGINT NULL,
  helpful BOOLEAN NOT NULL,
  primary_issue VARCHAR(40) NULL,
  issue_tags JSON NOT NULL,
  comment VARCHAR(4000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uk_ai_task_feedback_prompt UNIQUE(prompt_id),
  CONSTRAINT fk_ai_task_feedback_prompt FOREIGN KEY(prompt_id) REFERENCES ai_feedback_prompt(id),
  CONSTRAINT fk_ai_task_feedback_execution FOREIGN KEY(execution_id) REFERENCES ai_runtime_execution(id),
  CONSTRAINT fk_ai_task_feedback_document FOREIGN KEY(document_job_id) REFERENCES document_generation_job(id),
  CONSTRAINT fk_ai_task_feedback_template FOREIGN KEY(template_id) REFERENCES document_template(id),
  CONSTRAINT chk_ai_task_feedback_issue CHECK(primary_issue IS NULL OR primary_issue IN ('UNDERSTANDING_ERROR','CONTENT_ERROR','INCOMPLETE','POOR_REASONING','FORMAT_LAYOUT','STYLE_MISMATCH','SLOW_RESPONSE','OTHER')),
  CONSTRAINT chk_ai_task_feedback_consistency CHECK((helpful=TRUE AND primary_issue IS NULL) OR (helpful=FALSE AND primary_issue IS NOT NULL)),
  CONSTRAINT chk_ai_task_feedback_tags CHECK(JSON_TYPE(issue_tags)='ARRAY'),
  INDEX idx_ai_task_feedback_issue(primary_issue,created_at),
  INDEX idx_ai_task_feedback_user(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_feedback_skill_snapshot (
  feedback_id BIGINT NOT NULL,
  execution_node_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  skill_version_id BIGINT NOT NULL,
  skill_key_snapshot VARCHAR(100) NOT NULL,
  skill_version_snapshot VARCHAR(32) NOT NULL,
  node_status VARCHAR(32) NOT NULL,
  PRIMARY KEY(feedback_id,execution_node_id),
  CONSTRAINT fk_ai_feedback_skill_feedback FOREIGN KEY(feedback_id) REFERENCES ai_task_feedback(id),
  CONSTRAINT fk_ai_feedback_skill_node FOREIGN KEY(execution_node_id) REFERENCES ai_runtime_execution_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_feedback_provider_snapshot (
  feedback_id BIGINT NOT NULL,
  provider_invocation_id BIGINT NOT NULL,
  provider_key_snapshot VARCHAR(64) NOT NULL,
  model_key_snapshot VARCHAR(120) NOT NULL,
  invocation_status VARCHAR(20) NOT NULL,
  latency_ms BIGINT NULL,
  input_units BIGINT NOT NULL,
  output_units BIGINT NOT NULL,
  PRIMARY KEY(feedback_id,provider_invocation_id),
  CONSTRAINT fk_ai_feedback_provider_feedback FOREIGN KEY(feedback_id) REFERENCES ai_task_feedback(id),
  CONSTRAINT fk_ai_feedback_provider_invocation FOREIGN KEY(provider_invocation_id) REFERENCES ai_runtime_provider_invocation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_feedback_tool_snapshot (
  feedback_id BIGINT NOT NULL,
  execution_node_id BIGINT NOT NULL,
  tool_key_snapshot VARCHAR(100) NOT NULL,
  node_status VARCHAR(32) NOT NULL,
  PRIMARY KEY(feedback_id,execution_node_id),
  CONSTRAINT fk_ai_feedback_tool_feedback FOREIGN KEY(feedback_id) REFERENCES ai_task_feedback(id),
  CONSTRAINT fk_ai_feedback_tool_node FOREIGN KEY(execution_node_id) REFERENCES ai_runtime_execution_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_pending_skill_optimization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feedback_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  skill_version_id BIGINT NOT NULL,
  issue_type VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  priority INT NOT NULL DEFAULT 50,
  assignee_user_id BIGINT NULL,
  resolution_note VARCHAR(2000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  resolved_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_ai_pending_optimization UNIQUE(feedback_id,skill_version_id),
  CONSTRAINT fk_ai_pending_feedback FOREIGN KEY(feedback_id) REFERENCES ai_task_feedback(id),
  CONSTRAINT fk_ai_pending_skill FOREIGN KEY(skill_version_id,skill_id) REFERENCES ai_runtime_skill_version(id,skill_id),
  CONSTRAINT chk_ai_pending_status CHECK(status IN ('OPEN','IN_REVIEW','RESOLVED','DISMISSED')),
  CONSTRAINT chk_ai_pending_priority CHECK(priority BETWEEN 1 AND 100),
  INDEX idx_ai_pending_status(status,priority,created_at),
  INDEX idx_ai_pending_skill(skill_version_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
