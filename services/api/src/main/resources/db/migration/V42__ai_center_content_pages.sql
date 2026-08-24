CREATE TABLE ai_center_content_page (
  id BIGINT NOT NULL AUTO_INCREMENT,
  page_key VARCHAR(80) NOT NULL,
  title VARCHAR(200) NOT NULL,
  subtitle VARCHAR(500) NULL,
  content_markdown MEDIUMTEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
  version INT NOT NULL DEFAULT 1,
  updated_by BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_center_content_page_key (page_key),
  CONSTRAINT ck_ai_center_content_status CHECK (status IN ('DRAFT','PUBLISHED')),
  CONSTRAINT ck_ai_center_content_version CHECK (version > 0)
);

INSERT INTO ai_center_content_page(page_key,title,subtitle,content_markdown) VALUES
('USAGE_GUIDE','AI Center 使用说明','自然语言是唯一入口，平台会在后台选择合适的执行能力。',
'## 如何使用\n\n1. 在 AI Center 选择一个 Runtime。\n2. 直接描述你的学习目标，也可以添加资料。\n3. Workflow、Skill 和模型选择由平台在后台完成。\n4. 文件生成等重要任务会在对话中请求你确认框架与样式。\n\n> AI 可能会犯错，请核对课程事实、公式和重要结论。'),
('VCP_INTRO','VCP','一个为未来需求探索预留的独立 Runtime。',
'![VCP 概念视觉](https://images.unsplash.com/photo-1552664730-d307ca884978?auto=format&fit=crop&w=1600&q=80)\n\n## 从需求开始，而不是从答案开始\n\nVCP 将用于理解用户尚未完全表达清楚的需求，通过分阶段询问、方案探索和确认，形成可以执行的目标。\n\n### 计划能力\n\n- 需求访谈与澄清\n- 多轮方案比较\n- 用户确认节点\n- 将确认后的目标交给其他 Runtime\n\n当前页面用于介绍这一方向，执行能力将在后续需求明确后接入。');
