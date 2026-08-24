CREATE TABLE ai_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    skill_id VARCHAR(100) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    current_step INT NOT NULL DEFAULT 0,
    trace_id VARCHAR(64) NOT NULL,
    input_text MEDIUMTEXT NOT NULL,
    result_text LONGTEXT NULL,
    error_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_ai_task_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    UNIQUE KEY uk_ai_task_trace (trace_id),
    KEY idx_ai_task_user_created (user_id, created_at),
    KEY idx_ai_task_status_created (status, created_at)
);

CREATE TABLE ai_task_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    step_index INT NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_text LONGTEXT NULL,
    error_message VARCHAR(500) NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_ai_task_step_task FOREIGN KEY (task_id) REFERENCES ai_task(id) ON DELETE CASCADE,
    UNIQUE KEY uk_ai_task_step_index (task_id, step_index)
);
