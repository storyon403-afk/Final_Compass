CREATE TABLE survey_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prompt VARCHAR(300) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE survey_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  overall_suggestion VARCHAR(2000) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_survey_submission_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  INDEX idx_survey_submission_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE survey_answer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  submission_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  question_snapshot VARCHAR(300) NOT NULL,
  rating TINYINT NOT NULL,
  suggestion VARCHAR(1000) NULL,
  CONSTRAINT fk_survey_answer_submission FOREIGN KEY (submission_id) REFERENCES survey_submission(id) ON DELETE CASCADE,
  CONSTRAINT fk_survey_answer_question FOREIGN KEY (question_id) REFERENCES survey_question(id),
  CONSTRAINT chk_survey_rating CHECK (rating BETWEEN 1 AND 5),
  INDEX idx_survey_answer_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO survey_question(prompt,sort_order) VALUES
('实际使用中，这个系统对你的期末复习有多大帮助？',10),
('页面加载、资料查看和提交操作是否稳定流畅？',20),
('课程、老师和复习资料是否容易找到？',30),
('现有复习资料和讨论内容是否实用、可信？',40),
('你是否愿意在下一次期末复习时继续使用？',50);
