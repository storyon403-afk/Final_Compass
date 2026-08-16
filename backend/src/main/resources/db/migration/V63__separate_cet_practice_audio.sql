CREATE TABLE cet_practice_audio (
  paper_id BIGINT PRIMARY KEY,
  storage_name VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cet_practice_audio_paper FOREIGN KEY (paper_id) REFERENCES cet_paper(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 兼容此前误放在“完整套卷附件”中的音频，迁移后仅作为分类练习／精听精讲音频使用。
INSERT INTO cet_practice_audio(paper_id,storage_name,original_name)
SELECT paper_id,audio_storage_name,audio_original_name
FROM cet_paper_asset
WHERE audio_storage_name IS NOT NULL AND audio_original_name IS NOT NULL;
