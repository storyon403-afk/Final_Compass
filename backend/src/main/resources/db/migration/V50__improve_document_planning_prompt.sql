-- Strengthen the document-blueprint-planning skill prompt so the AI produces richer,
-- more structured Document Blueprints. The checksum is recomputed to match V38's scheme.

UPDATE ai_runtime_skill_version v JOIN ai_runtime_skill s ON s.id=v.skill_id
SET v.prompt_template=CONCAT(
  '你是资深文档蓝图规划 Skill。根据用户目标规划完整、可直接落稿的文档内容结构。',
  '只输出符合 Schema 的 JSON，不输出 Markdown 代码块；不得生成文件、不得声称文件已经存在。\n',
  '要求：\n',
  '1. 保持用户要求的语言、格式与风格。\n',
  '2. 章节数量与主题复杂度匹配：简单主题 3-5 节，复杂主题 6-12 节；避免空洞泛泛的章节，每节目的明确。\n',
  '3. 每个章节：title 简洁且信息量高；summary 为 2-4 句有实质内容的概述（给出结论、背景或要点，不写套话）；',
  'bullets 3-8 条，具体、可操作、信息密度高，尽量包含事实、步骤、数据或示例，避免重复与空泛。\n',
  '4. 按格式调整粒度：PPTX 要点用精炼短句；DOCX 与 PDF 的 summary 可更充实；XLSX 要点偏向可整理为行列的条目。\n',
  '5. 不得编造目标与输入中不存在的具体事实、数据或引用；不确定时给出通用而稳妥的内容。')
WHERE s.skill_key='document-blueprint-planning' AND v.version='1.0.0';

UPDATE ai_runtime_skill_version v JOIN ai_runtime_skill s ON s.id=v.skill_id
SET v.checksum=SHA2(CONCAT_WS(CHAR(31),s.skill_key,v.version,v.executor_type,v.executor_key,
  CAST(v.input_schema AS CHAR),CAST(v.output_schema AS CHAR),v.prompt_template,v.output_contract,
  CAST(v.required_capabilities AS CHAR),CAST(v.permission_policy AS CHAR),CAST(v.allowed_tools AS CHAR),
  CAST(v.configuration AS CHAR),v.max_input_units,v.timeout_ms,CAST(v.retry_policy AS CHAR)),256)
WHERE s.skill_key='document-blueprint-planning' AND v.version='1.0.0';
