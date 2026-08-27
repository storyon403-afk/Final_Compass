CREATE TABLE question_vine_topic (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sequence_no INT NOT NULL,
  author_id BIGINT,
  anonymous_name VARCHAR(40) NOT NULL,
  title VARCHAR(100) NOT NULL,
  category VARCHAR(30) NOT NULL,
  tags_json JSON NOT NULL,
  body TEXT NOT NULL,
  status ENUM('OPEN','SOLVED') NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_question_vine_sequence (sequence_no),
  INDEX idx_question_vine_created (created_at),
  CONSTRAINT fk_question_vine_author FOREIGN KEY (author_id) REFERENCES app_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_vine_answer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  topic_id BIGINT NOT NULL,
  author_id BIGINT,
  anonymous_name VARCHAR(40) NOT NULL,
  content TEXT NOT NULL,
  helpful_count INT NOT NULL DEFAULT 0,
  accepted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_question_vine_answer_topic (topic_id, created_at),
  CONSTRAINT fk_question_vine_answer_topic FOREIGN KEY (topic_id) REFERENCES question_vine_topic(id) ON DELETE CASCADE,
  CONSTRAINT fk_question_vine_answer_author FOREIGN KEY (author_id) REFERENCES app_user(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_vine_moderation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  deleted_topic_id BIGINT NOT NULL,
  deleted_sequence_no INT NOT NULL,
  title_snapshot VARCHAR(100) NOT NULL,
  category_snapshot VARCHAR(30) NOT NULL,
  admin_id BIGINT NOT NULL,
  answer_count INT NOT NULL,
  deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_question_vine_audit_time (deleted_at),
  CONSTRAINT fk_question_vine_audit_admin FOREIGN KEY (admin_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO question_vine_topic(sequence_no,anonymous_name,title,category,tags_json,body,status,created_at) VALUES
(1,'匿名同学 A7K3','问题藤从这里开始','藤上留言',JSON_ARRAY('第一片叶子'),'把你的困惑留在这里。它会沿着时间生长，也可能成为后来者找到的答案。','SOLVED','2026-10-01 08:00:00'),
(2,'匿名同学 T4C9','新生选课有什么容易忽略的地方？','校园生活',JSON_ARRAY('选课','新生'),'第一次选课，想知道时间冲突、补选和退选有什么需要注意的。','SOLVED','2026-10-03 08:00:00'),
(3,'匿名同学 B8R2','校园网晚上经常断开怎么办？','校园生活',JSON_ARRAY('校园网','宿舍'),'宿舍晚上经常掉线，手机和电脑都会发生。','OPEN','2026-10-06 08:00:00'),
(4,'匿名同学 C2X5','有没有同学想组队参加蓝桥杯？','竞赛组队',JSON_ARRAY('蓝桥杯','找队友'),'目前两个人，希望再找一位会 Java 或 Python 的同学一起准备。','OPEN','2026-10-09 08:00:00'),
(5,'匿名同学 H3N6','四级听力每天练多久比较合适？','学习考试',JSON_ARRAY('CET-4','听力'),'距离考试还有两个月，目前听力错得比较多。','SOLVED','2026-10-12 08:00:00'),
(6,'匿名同学 Q7L4','MATLAB 安装后打开一直白屏','学习工具',JSON_ARRAY('MATLAB','求助'),'安装完成了，但启动界面只有白屏，重装也没解决。','SOLVED','2026-10-14 08:00:00'),
(7,'匿名同学 J4P1','大一现在参加竞赛会不会太早？','竞赛组队',JSON_ARRAY('入门','组队'),'刚开始学编程，想找一个能跟着学的竞赛，不知道从哪个开始合适。','OPEN','2026-10-16 08:00:00'),
(8,'匿名同学 M2P8','数学建模要换什么样的电脑？','电脑数码',JSON_ARRAY('数学建模','电脑配置'),'预算大概 5000，平时需要跑 Python、MATLAB，偶尔训练一些小模型。有没有比较均衡的配置建议？','OPEN','2026-10-18 08:00:00');

INSERT INTO question_vine_answer(topic_id,anonymous_name,content,helpful_count,accepted) VALUES
((SELECT id FROM question_vine_topic WHERE sequence_no=2),'匿名同学 T4C9','先看培养方案和必修先修关系，不要只看上课时间，还要留意考试时间冲突。',9,TRUE),
((SELECT id FROM question_vine_topic WHERE sequence_no=3),'匿名同学 B8R2','先记录掉线时间和宿舍楼层，可能是 AP 负载问题，一起反馈给网络中心会更快。',5,FALSE),
((SELECT id FROM question_vine_topic WHERE sequence_no=5),'匿名同学 H3N6','比起一次听很久，每天 20–30 分钟精听更容易坚持，最后要复盘错误原因。',16,TRUE),
((SELECT id FROM question_vine_topic WHERE sequence_no=6),'匿名同学 Q7L4','可以先更新显卡驱动，然后用 -softwareopengl 参数启动试一下。',8,TRUE),
((SELECT id FROM question_vine_topic WHERE sequence_no=8),'匿名同学 M2P8','普通建模不用追求高端显卡，优先 32GB 内存和散热。需要深度学习时再考虑 NVIDIA 显卡。',12,FALSE);
