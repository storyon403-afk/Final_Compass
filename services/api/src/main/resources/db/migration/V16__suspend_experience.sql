CREATE TABLE suspend_setting (
  id TINYINT PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  play_mode VARCHAR(16) NOT NULL DEFAULT 'FIXED',
  fixed_video_id BIGINT NULL,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_suspend_setting_id CHECK (id = 1),
  CONSTRAINT chk_suspend_play_mode CHECK (play_mode IN ('FIXED', 'RANDOM')),
  CONSTRAINT fk_suspend_setting_admin FOREIGN KEY (updated_by) REFERENCES app_user(id)
);

CREATE TABLE suspend_video (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  display_name VARCHAR(120) NOT NULL,
  storage_name VARCHAR(160) NOT NULL UNIQUE,
  content_type VARCHAR(50) NOT NULL,
  size_bytes BIGINT NOT NULL,
  duration_seconds INT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  uploaded_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_suspend_video_duration CHECK (duration_seconds BETWEEN 1 AND 30),
  CONSTRAINT fk_suspend_video_admin FOREIGN KEY (uploaded_by) REFERENCES app_user(id)
);

ALTER TABLE suspend_setting
  ADD CONSTRAINT fk_suspend_setting_video FOREIGN KEY (fixed_video_id) REFERENCES suspend_video(id) ON DELETE SET NULL;

INSERT INTO suspend_setting(id, enabled, play_mode) VALUES (1, TRUE, 'FIXED');
