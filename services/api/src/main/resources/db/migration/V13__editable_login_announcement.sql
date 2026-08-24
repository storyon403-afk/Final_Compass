CREATE TABLE system_announcement (
  id TINYINT PRIMARY KEY,
  content VARCHAR(1000) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_announcement_admin FOREIGN KEY (updated_by) REFERENCES app_user(id)
);

INSERT INTO system_announcement(id, content, enabled)
VALUES (1, '感谢大家的支持，请一定给予我们该系统的评价，建议以及意见呢，拜托了！', TRUE);
