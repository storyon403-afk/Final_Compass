CREATE TABLE anonymous_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  public_id CHAR(36) NOT NULL UNIQUE,
  nickname VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  slug VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(32),
  category VARCHAR(40) NOT NULL,
  college VARCHAR(100) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teacher (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  slug VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(40) NOT NULL,
  college VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teacher_course (
  teacher_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  term VARCHAR(40) NOT NULL,
  review_note VARCHAR(500),
  PRIMARY KEY (teacher_id, course_id, term),
  CONSTRAINT fk_tc_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
  CONSTRAINT fk_tc_course FOREIGN KEY (course_id) REFERENCES course(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  uploader_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  resource_type VARCHAR(30) NOT NULL,
  description VARCHAR(500),
  original_name VARCHAR(255) NOT NULL,
  storage_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100),
  file_size BIGINT NOT NULL,
  status ENUM('PENDING', 'PUBLISHED', 'REJECTED') NOT NULL DEFAULT 'PUBLISHED',
  download_count INT NOT NULL DEFAULT 0,
  thanks_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_resource_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
  CONSTRAINT fk_resource_course FOREIGN KEY (course_id) REFERENCES course(id),
  CONSTRAINT fk_resource_user FOREIGN KEY (uploader_id) REFERENCES anonymous_user(id),
  INDEX idx_resource_circle (teacher_id, course_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE discussion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  content VARCHAR(500) NOT NULL,
  like_count INT NOT NULL DEFAULT 0,
  status ENUM('VISIBLE', 'HIDDEN') NOT NULL DEFAULT 'VISIBLE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_discussion_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
  CONSTRAINT fk_discussion_course FOREIGN KEY (course_id) REFERENCES course(id),
  CONSTRAINT fk_discussion_user FOREIGN KEY (author_id) REFERENCES anonymous_user(id),
  CONSTRAINT fk_discussion_parent FOREIGN KEY (parent_id) REFERENCES discussion(id),
  INDEX idx_discussion_circle (teacher_id, course_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO course (slug, name, code, category, college) VALUES
('data-structure', '数据结构', 'CS203', '计算机基础', '计算机学院'),
('java', 'Java 程序设计', 'CS205', '计算机基础', '计算机学院'),
('network', '计算机网络', 'CS301', '计算机基础', '计算机学院');

INSERT INTO teacher (slug, name, college) VALUES
('lin', '林老师', '计算机学院'), ('zhou', '周老师', '计算机学院'), ('chen', '陈老师', '计算机学院');

INSERT INTO teacher_course (teacher_id, course_id, term, review_note)
SELECT t.id, c.id, '2025—2026 学年第 2 学期', '同学共建页面，具体考试范围以课堂通知为准。'
FROM teacher t CROSS JOIN course c WHERE c.slug = 'data-structure';

