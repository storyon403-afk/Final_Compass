CREATE TABLE ai_prompt_template (

    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    skill_id VARCHAR(100) NOT NULL,

    version VARCHAR(50) NOT NULL,

    system_prompt TEXT NOT NULL,

    output_contract TEXT,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,


    UNIQUE KEY uk_skill_version
    (
        skill_id,
        version
    )

);