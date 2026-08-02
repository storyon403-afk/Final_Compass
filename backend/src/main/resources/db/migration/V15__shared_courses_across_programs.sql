-- 课程本身由课程代码唯一标识；专业、课程类型属于课程与专业之间的关系。
CREATE TABLE course_program (
  course_id BIGINT NOT NULL,
  college VARCHAR(100) NOT NULL,
  program_name VARCHAR(80) NOT NULL,
  course_type VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (course_id, college, program_name),
  CONSTRAINT fk_course_program_course FOREIGN KEY (course_id) REFERENCES course(id),
  CONSTRAINT chk_course_program_type CHECK (course_type IN ('专业课', '非专业课')),
  INDEX idx_course_program_navigation (college, program_name, course_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO course_program(course_id, college, program_name, course_type)
SELECT id,
       college,
       COALESCE(NULLIF(program_name, ''),
         CASE
           WHEN college = '数学与统计学院' AND CONCAT(name, ' ', category) REGEXP '统计|概率|随机' THEN '统计学'
           WHEN college = '数学与统计学院' THEN '数学与应用数学'
           ELSE '未分专业'
         END),
       COALESCE(NULLIF(course_type, ''),
         CASE WHEN category IN ('数学与统计', '专业核心课') THEN '专业课' ELSE '非专业课' END)
FROM course
WHERE active = TRUE;

UPDATE course SET code = NULL WHERE TRIM(COALESCE(code, '')) = '';
-- 生产历史数据可能存在同码异课，不能在迁移中猜测性合并或删除。
-- 新增接口负责阻止新的重复代码；普通索引保证按代码查找效率。
ALTER TABLE course ADD INDEX idx_course_code (code);
