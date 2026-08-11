CREATE TABLE ai_skill_change_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_key VARCHAR(64) NOT NULL,
  recommendation_id BIGINT NULL,
  skill_id BIGINT NOT NULL,
  base_version_id BIGINT NOT NULL,
  draft_version_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  rationale VARCHAR(2000) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  created_by BIGINT NOT NULL,
  reviewed_by BIGINT NULL,
  review_note VARCHAR(2000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  reviewed_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_ai_skill_change_request_key UNIQUE(request_key),
  CONSTRAINT uk_ai_skill_change_draft UNIQUE(draft_version_id),
  CONSTRAINT fk_ai_skill_change_recommendation FOREIGN KEY(recommendation_id) REFERENCES ai_skill_optimization_recommendation(id),
  CONSTRAINT fk_ai_skill_change_base FOREIGN KEY(base_version_id,skill_id) REFERENCES ai_runtime_skill_version(id,skill_id),
  CONSTRAINT fk_ai_skill_change_draft FOREIGN KEY(draft_version_id,skill_id) REFERENCES ai_runtime_skill_version(id,skill_id),
  CONSTRAINT chk_ai_skill_change_versions CHECK(base_version_id<>draft_version_id),
  CONSTRAINT chk_ai_skill_change_status CHECK(status IN ('DRAFT','EVALUATED','APPROVED','REJECTED','PUBLISHED')),
  INDEX idx_ai_skill_change_status(status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_skill_evaluation_case (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  skill_id BIGINT NOT NULL,
  case_key VARCHAR(100) NOT NULL,
  name VARCHAR(200) NOT NULL,
  input_payload JSON NOT NULL,
  candidate_output JSON NOT NULL,
  required_output_paths JSON NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_ai_skill_eval_case_skill FOREIGN KEY(skill_id) REFERENCES ai_runtime_skill(id),
  CONSTRAINT uk_ai_skill_eval_case UNIQUE(skill_id,case_key),
  CONSTRAINT chk_ai_skill_eval_case_json CHECK(JSON_TYPE(input_payload)='OBJECT' AND JSON_TYPE(candidate_output)='OBJECT' AND JSON_TYPE(required_output_paths)='ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_skill_evaluation_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_key VARCHAR(64) NOT NULL,
  change_request_id BIGINT NOT NULL,
  skill_version_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
  total_cases INT NOT NULL DEFAULT 0,
  passed_cases INT NOT NULL DEFAULT 0,
  failed_cases INT NOT NULL DEFAULT 0,
  initiated_by BIGINT NOT NULL,
  started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_ai_skill_eval_run_key UNIQUE(run_key),
  CONSTRAINT fk_ai_skill_eval_run_change FOREIGN KEY(change_request_id) REFERENCES ai_skill_change_request(id),
  CONSTRAINT fk_ai_skill_eval_run_version FOREIGN KEY(skill_version_id) REFERENCES ai_runtime_skill_version(id),
  CONSTRAINT chk_ai_skill_eval_run_status CHECK(status IN ('RUNNING','PASSED','FAILED')),
  INDEX idx_ai_skill_eval_run_change(change_request_id,started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_skill_evaluation_result (
  evaluation_run_id BIGINT NOT NULL,
  evaluation_case_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  failure_summary VARCHAR(1000) NULL,
  output_digest CHAR(64) NOT NULL,
  PRIMARY KEY(evaluation_run_id,evaluation_case_id),
  CONSTRAINT fk_ai_skill_eval_result_run FOREIGN KEY(evaluation_run_id) REFERENCES ai_skill_evaluation_run(id),
  CONSTRAINT fk_ai_skill_eval_result_case FOREIGN KEY(evaluation_case_id) REFERENCES ai_skill_evaluation_case(id),
  CONSTRAINT chk_ai_skill_eval_result_status CHECK(status IN ('PASSED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_skill_release (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  release_key VARCHAR(64) NOT NULL,
  change_request_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  skill_version_id BIGINT NOT NULL,
  previous_version_id BIGINT NOT NULL,
  rollout_percentage INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  released_by BIGINT NOT NULL,
  released_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  rolled_back_by BIGINT NULL,
  rolled_back_at TIMESTAMP(6) NULL,
  rollback_note VARCHAR(2000) NULL,
  CONSTRAINT uk_ai_skill_release_key UNIQUE(release_key),
  CONSTRAINT uk_ai_skill_release_change UNIQUE(change_request_id),
  CONSTRAINT fk_ai_skill_release_change FOREIGN KEY(change_request_id) REFERENCES ai_skill_change_request(id),
  CONSTRAINT fk_ai_skill_release_version FOREIGN KEY(skill_version_id,skill_id) REFERENCES ai_runtime_skill_version(id,skill_id),
  CONSTRAINT fk_ai_skill_release_previous FOREIGN KEY(previous_version_id,skill_id) REFERENCES ai_runtime_skill_version(id,skill_id),
  CONSTRAINT chk_ai_skill_release_rollout CHECK(rollout_percentage BETWEEN 1 AND 100),
  CONSTRAINT chk_ai_skill_release_status CHECK(status IN ('CANARY','ACTIVE','ROLLED_BACK')),
  INDEX idx_ai_skill_release_selection(skill_id,status,released_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
