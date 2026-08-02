CREATE TABLE cet_paper_asset (
  paper_id BIGINT PRIMARY KEY,
  source_name VARCHAR(120) NOT NULL,
  source_page_url VARCHAR(500) NOT NULL,
  usage_note VARCHAR(500) NOT NULL,
  question_storage_name VARCHAR(255),
  question_original_name VARCHAR(255),
  answer_storage_name VARCHAR(255),
  answer_original_name VARCHAR(255),
  audio_storage_name VARCHAR(255),
  audio_original_name VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cet_paper_asset_paper FOREIGN KEY (paper_id) REFERENCES cet_paper(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 公开仓库只提供附件结构，不分发第三方真题、答案或音频。
