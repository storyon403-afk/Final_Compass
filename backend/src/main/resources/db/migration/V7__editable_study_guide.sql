CREATE TABLE study_guide (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  content_markdown TEXT NOT NULL,
  change_note VARCHAR(500) NOT NULL DEFAULT '',
  updated_by BIGINT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_study_guide_circle UNIQUE (course_id, teacher_id),
  CONSTRAINT fk_guide_course FOREIGN KEY (course_id) REFERENCES course(id),
  CONSTRAINT fk_guide_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
  CONSTRAINT fk_guide_editor FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE guide_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  content_markdown TEXT NOT NULL,
  status ENUM('PENDING','APPROVED','REJECTED','INCORPORATED') NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at TIMESTAMP NULL,
  incorporated_at TIMESTAMP NULL,
  CONSTRAINT fk_guide_submission_course FOREIGN KEY (course_id) REFERENCES course(id),
  CONSTRAINT fk_guide_submission_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
  CONSTRAINT fk_guide_submission_author FOREIGN KEY (author_id) REFERENCES anonymous_user(id),
  INDEX idx_guide_submission_review (status, created_at),
  INDEX idx_guide_submission_circle (course_id, teacher_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE moderation_audit
  MODIFY item_type ENUM('RESOURCE','DISCUSSION','GUIDE_SUBMISSION') NOT NULL;

