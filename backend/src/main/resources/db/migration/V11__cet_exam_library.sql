CREATE TABLE cet_paper (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  level ENUM('CET4','CET6') NOT NULL,
  exam_year SMALLINT NOT NULL,
  exam_month TINYINT NOT NULL,
  set_number TINYINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  published BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cet_paper (level, exam_year, exam_month, set_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cet_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  paper_id BIGINT NOT NULL,
  mode ENUM('PRACTICE','INTENSIVE') NOT NULL,
  section VARCHAR(32) NOT NULL,
  title VARCHAR(160) NOT NULL,
  prompt TEXT,
  passage MEDIUMTEXT,
  translation MEDIUMTEXT,
  analysis MEDIUMTEXT,
  key_sentence TEXT,
  answer_type ENUM('CHOICE','TEXT') NOT NULL DEFAULT 'CHOICE',
  options_json JSON,
  correct_answer TEXT,
  item_order INT NOT NULL DEFAULT 0,
  audio_storage_name VARCHAR(255),
  audio_original_name VARCHAR(255),
  audio_mime_type VARCHAR(100),
  audio_start_ms INT,
  audio_end_ms INT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cet_item_paper FOREIGN KEY (paper_id) REFERENCES cet_paper(id) ON DELETE CASCADE,
  INDEX idx_cet_item_filter (paper_id, mode, section, item_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cet_paper(level, exam_year, exam_month, set_number, title) VALUES
('CET4', 2025, 12, 1, '2025 年 12 月四级 · 第一套（示例）'),
('CET6', 2025, 12, 1, '2025 年 12 月六级 · 第一套（示例）');

INSERT INTO cet_item(
  paper_id, mode, section, title, prompt, passage, translation, analysis,
  key_sentence, answer_type, options_json, correct_answer, item_order
) VALUES
((SELECT id FROM cet_paper WHERE level='CET4' AND exam_year=2025 AND exam_month=12 AND set_number=1),
 'PRACTICE','WRITING','Writing · Campus learning spaces',
 'Write an essay about how a university can create better shared learning spaces. You should write at least 120 words.',
 NULL,
 '请围绕大学如何建设更好的共享学习空间写一篇不少于 120 词的短文。',
 '示例题重点考查观点展开。建议用“现状—措施—预期效果”的三段结构，并用具体场景支撑观点。',
 NULL,'TEXT',NULL,
 'A strong response states a clear position, develops two practical measures, and ends with their expected impact.',
 10),
((SELECT id FROM cet_paper WHERE level='CET4' AND exam_year=2025 AND exam_month=12 AND set_number=1),
 'PRACTICE','CAREFUL_READING','Careful reading · Question 1',
 'What is the main reason the library extended its evening hours?',
 'A university library recently tested longer evening hours during the final-exam period. Attendance data showed that many students arrived after laboratory classes ended. The library therefore kept two floors open until midnight and added a quiet shuttle stop near the entrance. According to the survey that followed, students valued the predictable schedule more than the additional seating.',
 '一所大学图书馆最近在期末考试期间试行延长晚间开放时间。到馆数据表明，许多学生会在实验课结束后前来。因此，图书馆将两层开放至午夜，并在入口附近增设安静区域的接驳车站。后续调查显示，比起新增座位，学生更看重稳定、可预期的开放安排。',
 '定位首句与第二句。延长开放时间是为了服务实验课结束后才到馆的学生；末句是调查结果，不是最初原因。',
 'Attendance data showed that many students arrived after laboratory classes ended.',
 'CHOICE', JSON_ARRAY(
   'To provide more seats for visitors.',
   'To serve students finishing laboratory classes late.',
   'To reduce the cost of daytime staffing.',
   'To test a new shuttle route.'
 ), 'B', 20),
((SELECT id FROM cet_paper WHERE level='CET4' AND exam_year=2025 AND exam_month=12 AND set_number=1),
 'PRACTICE','TRANSLATION','Translation · Community libraries',
 'Translate the following paragraph into English.',
 '近年来，越来越多的社区图书馆延长了开放时间，并组织阅读分享活动。这些空间不仅方便居民借阅图书，也为不同年龄的人提供了交流和学习的机会。',
 'In recent years, a growing number of community libraries have extended their opening hours and organized reading events. These spaces not only make it convenient for residents to borrow books, but also provide people of different ages with opportunities to communicate and learn.',
 '注意“越来越多”可译为 a growing number of；“不仅……也……”使用 not only... but also... 保持并列结构。',
 NULL,'TEXT',NULL,
 'In recent years, a growing number of community libraries have extended their opening hours and organized reading events.',
 30),
((SELECT id FROM cet_paper WHERE level='CET4' AND exam_year=2025 AND exam_month=12 AND set_number=1),
 'INTENSIVE','LONG_CONVERSATION','Long conversation · Sentence 1',
 'Listen to the sentence, then reveal the key line and explanation.',
 'M: I thought the workshop started at nine, but the notice says the first group should arrive fifteen minutes earlier.',
 '男：我原以为研讨会九点开始，但通知说第一组应提前十五分钟到达。',
 '时间信息发生修正：不是九点到达，而是需要提前十五分钟。听力中 but 后面的信息通常更关键。',
 'the first group should arrive fifteen minutes earlier',
 'CHOICE', JSON_ARRAY('8:30','8:45','9:00','9:15'), 'B', 10),
((SELECT id FROM cet_paper WHERE level='CET6' AND exam_year=2025 AND exam_month=12 AND set_number=1),
 'INTENSIVE','LECTURE','Lecture · Sentence 1',
 'Listen for the speaker’s central contrast.',
 'What matters is not the amount of information collected, but whether researchers can explain why the pattern appears.',
 '重要的不是收集了多少信息，而是研究人员能否解释这种模式为何出现。',
 'not... but... 给出明确转折，关键观点位于 but 之后。',
 'whether researchers can explain why the pattern appears',
 'CHOICE', JSON_ARRAY(
   'The volume of information is always decisive.',
   'Explaining a pattern is more important than merely collecting data.',
   'Researchers should avoid identifying patterns.',
   'Only small data sets can be explained.'
 ), 'B', 10);
