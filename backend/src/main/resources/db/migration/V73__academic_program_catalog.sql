CREATE TABLE academic_program (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  college_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_academic_program_college_name (college_id, name),
  CONSTRAINT fk_academic_program_college FOREIGN KEY (college_id) REFERENCES college(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO academic_program(college_id, name)
SELECT DISTINCT c.id, cp.program_name
FROM course_program cp
JOIN college c ON c.name = cp.college
WHERE cp.program_name IS NOT NULL AND TRIM(cp.program_name) <> '';

INSERT IGNORE INTO academic_program(college_id, name)
SELECT id, '数学类' FROM college WHERE name = '数学与统计学院';

INSERT IGNORE INTO academic_program(college_id, name)
SELECT id, '数学与应用数学' FROM college WHERE name = '数学与统计学院';

INSERT IGNORE INTO academic_program(college_id, name)
SELECT id, '统计学' FROM college WHERE name = '数学与统计学院';
