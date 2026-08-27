ALTER TABLE question_vine_answer
  ADD COLUMN parent_answer_id BIGINT NULL AFTER topic_id,
  ADD CONSTRAINT fk_question_vine_answer_parent
    FOREIGN KEY (parent_answer_id) REFERENCES question_vine_answer(id) ON DELETE CASCADE,
  ADD INDEX idx_question_vine_answer_parent (parent_answer_id);

CREATE TABLE site_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  recipient_user_id BIGINT NOT NULL,
  sender_user_id BIGINT NULL,
  kind ENUM('SYSTEM','ADMIN_DIRECT','ADMIN_BROADCAST','USER_TO_ADMIN') NOT NULL,
  subject VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  link_path VARCHAR(500) NULL,
  read_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_site_message_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_site_message_sender FOREIGN KEY (sender_user_id) REFERENCES app_user(id) ON DELETE SET NULL,
  INDEX idx_site_message_inbox (recipient_user_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
