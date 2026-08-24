CREATE TABLE document_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_key VARCHAR(100) NOT NULL,
  name VARCHAR(160) NOT NULL,
  format VARCHAR(20) NOT NULL,
  style_family VARCHAR(80) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  configuration JSON NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT uk_document_template_key UNIQUE(template_key),
  CONSTRAINT chk_document_template_format CHECK(format IN ('HTML','PDF','PPTX','DOCX','XLSX')),
  CONSTRAINT chk_document_template_status CHECK(status IN ('ACTIVE','DISABLED')),
  CONSTRAINT chk_document_template_config CHECK(JSON_TYPE(configuration)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document_generation_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_key VARCHAR(80) NOT NULL,
  user_id BIGINT NOT NULL,
  trace_id VARCHAR(80) NULL,
  format VARCHAR(20) NOT NULL,
  template_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'OUTLINE_PENDING',
    blueprint JSON NOT NULL,
    blueprint_version INT NOT NULL DEFAULT 1,
    lock_version INT NOT NULL DEFAULT 0,
  outline_feedback VARCHAR(2000) NULL,
  style_feedback VARCHAR(2000) NULL,
  error_code VARCHAR(80) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_document_generation_job_key UNIQUE(job_key),
  CONSTRAINT fk_document_generation_template FOREIGN KEY(template_id) REFERENCES document_template(id),
  CONSTRAINT chk_document_generation_status CHECK(status IN ('OUTLINE_PENDING','STYLE_PREVIEW_PENDING','GENERATING','COMPLETED','FAILED','CANCELLED')),
  CONSTRAINT chk_document_generation_blueprint CHECK(JSON_TYPE(blueprint)='OBJECT'),
  INDEX idx_document_generation_user(user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document_artifact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  artifact_type VARCHAR(20) NOT NULL,
  storage_name VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(160) NOT NULL,
  size_bytes BIGINT NOT NULL,
  content_digest CHAR(64) NOT NULL,
  page_count INT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_document_artifact_job FOREIGN KEY(job_id) REFERENCES document_generation_job(id),
  CONSTRAINT uk_document_artifact_storage UNIQUE(storage_name),
  CONSTRAINT chk_document_artifact_type CHECK(artifact_type IN ('PREVIEW','FINAL')),
  CONSTRAINT chk_document_artifact_size CHECK(size_bytes>0),
  INDEX idx_document_artifact_job(job_id,artifact_type,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO document_template(template_key,name,format,style_family,description,configuration) VALUES
 ('academic-minimal-html','学术极简 HTML','HTML','ACADEMIC_MINIMAL','清晰标题、适度留白和打印友好布局。',JSON_OBJECT('primaryColor','#1f4b7a','accentColor','#2f80ed','fontFamily','system-ui')),
 ('academic-minimal-pdf','学术极简 PDF','PDF','ACADEMIC_MINIMAL','HTML Blueprint 经 Playwright 打印为 PDF。',JSON_OBJECT('primaryColor','#1f4b7a','accentColor','#2f80ed','fontFamily','system-ui')),
 ('academic-minimal-pptx','学术极简 PPT','PPTX','ACADEMIC_MINIMAL','16:9 可编辑演示文稿。',JSON_OBJECT('primaryColor','1F4B7A','accentColor','2F80ED','fontFamily','Aptos')),
 ('academic-minimal-docx','学术极简 Word','DOCX','ACADEMIC_MINIMAL','可编辑标题层级和正文段落。',JSON_OBJECT('primaryColor','1F4B7A','fontFamily','Aptos')),
 ('academic-minimal-xlsx','学术极简 Excel','XLSX','ACADEMIC_MINIMAL','按章节拆分工作表的可编辑表格。',JSON_OBJECT('primaryColor','1F4B7A','accentColor','2F80ED','fontFamily','Aptos'));
