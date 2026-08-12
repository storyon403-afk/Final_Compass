INSERT INTO ai_runtime_skill(skill_key,name,skill_type,description,status,risk_level,domain_tags)
VALUES ('document-blueprint-planning','文档蓝图规划','PLANNING','将用户的文档目标转换为可校验的结构化 Document Blueprint，不直接生成文件。','ACTIVE','LOW',JSON_ARRAY('DOCUMENT','PLANNING'));

INSERT INTO ai_runtime_skill_version(
  skill_id,version,lifecycle_status,executor_type,executor_key,input_schema,output_schema,
  prompt_template,output_contract,required_capabilities,permission_policy,allowed_tools,
  configuration,max_input_units,timeout_ms,retry_policy,checksum,published_at
)
SELECT s.id,'1.0.0','PUBLISHED','LLM_PROMPT','provider-prompt-v1',
  JSON_OBJECT('type','object','properties',JSON_OBJECT(
    'goal',JSON_OBJECT('type','string','minLength',1),
    'format',JSON_OBJECT('type','string','enum',JSON_ARRAY('HTML','PDF','PPTX','DOCX','XLSX')),
    'style',JSON_OBJECT('type','string'),'templateKey',JSON_OBJECT('type','string')),
    'required',JSON_ARRAY('goal','format','style','templateKey'),'additionalProperties',TRUE),
  JSON_OBJECT('type','object','properties',JSON_OBJECT(
    'title',JSON_OBJECT('type','string'),'subject',JSON_OBJECT('type','string'),
    'style',JSON_OBJECT('type','string'),'format',JSON_OBJECT('type','string','enum',JSON_ARRAY('HTML','PDF','PPTX','DOCX','XLSX')),
    'sections',JSON_OBJECT('type','array','minItems',1,'maxItems',100,'items',JSON_OBJECT(
      'type','object','properties',JSON_OBJECT('title',JSON_OBJECT('type','string'),
      'summary',JSON_OBJECT('type','string'),'bullets',JSON_OBJECT('type','array','items',JSON_OBJECT('type','string'))),
      'required',JSON_ARRAY('title','summary','bullets'),'additionalProperties',FALSE))),
    'required',JSON_ARRAY('title','subject','style','format','sections'),'additionalProperties',FALSE),
  '你是文档蓝图规划 Skill。根据用户目标规划完整内容结构。只输出符合 Schema 的 JSON；不得生成文件、不得声称文件已经存在。每个章节包含标题、摘要和要点，并保持用户要求的语言、格式和风格。',
  '输出 Document Blueprint JSON，不输出 Markdown 代码块。',JSON_ARRAY('TEXT_REASONING'),
  JSON_OBJECT('authenticated',TRUE,'permissions',JSON_ARRAY()),JSON_ARRAY(),
  JSON_OBJECT('templateFormat','DOCUMENT_BLUEPRINT_V1','structuredOutput',TRUE),20000,90000,
  JSON_OBJECT('maxAttempts',2),REPEAT('0',64),CURRENT_TIMESTAMP
FROM ai_runtime_skill s WHERE s.skill_key='document-blueprint-planning';

UPDATE ai_runtime_skill_version v JOIN ai_runtime_skill s ON s.id=v.skill_id
SET v.checksum=SHA2(CONCAT_WS(CHAR(31),s.skill_key,v.version,v.executor_type,v.executor_key,
  CAST(v.input_schema AS CHAR),CAST(v.output_schema AS CHAR),v.prompt_template,v.output_contract,
  CAST(v.required_capabilities AS CHAR),CAST(v.permission_policy AS CHAR),CAST(v.allowed_tools AS CHAR),
  CAST(v.configuration AS CHAR),v.max_input_units,v.timeout_ms,CAST(v.retry_policy AS CHAR)),256)
WHERE s.skill_key='document-blueprint-planning' AND v.version='1.0.0';

UPDATE ai_runtime_skill s JOIN ai_runtime_skill_version v ON v.skill_id=s.id AND v.version='1.0.0'
SET s.current_version_id=v.id WHERE s.skill_key='document-blueprint-planning';

INSERT INTO ai_runtime_workflow(workflow_key,name,description,status,runtime_type,resolver_tags)
VALUES ('document-generation','确认式文档生成','规划文档蓝图，经大纲和样式确认后生成可下载文件。','ACTIVE','WORKFLOW',JSON_ARRAY('DOCUMENT','GENERATION','PREVIEW_CONFIRM'));

INSERT INTO ai_runtime_workflow_version(workflow_id,version,lifecycle_status,input_schema,output_schema,resolver_policy,session_policy,max_parallelism,timeout_ms)
SELECT w.id,'1.0.0','DRAFT',
  JSON_OBJECT('type','object','properties',JSON_OBJECT(
    'goal',JSON_OBJECT('type','string','minLength',1),
    'format',JSON_OBJECT('type','string','enum',JSON_ARRAY('HTML','PDF','PPTX','DOCX','XLSX')),
    'style',JSON_OBJECT('type','string'),'templateKey',JSON_OBJECT('type','string')),
    'required',JSON_ARRAY('goal','format','style','templateKey'),'additionalProperties',FALSE),
  JSON_OBJECT('type','object','properties',JSON_OBJECT('jobKey',JSON_OBJECT('type','string'),
    'status',JSON_OBJECT('type','string'),'artifacts',JSON_OBJECT('type','array')),
    'required',JSON_ARRAY('jobKey','status','artifacts'),'additionalProperties',TRUE),
  JSON_OBJECT('strategy','DYNAMIC','taskTypes',JSON_ARRAY('DOCUMENT_GENERATION'),'outputArtifacts',JSON_ARRAY('PDF','PPTX','DOCX','XLSX','HTML')),
  JSON_OBJECT('memoryType','SESSION','releaseOnTerminalState',TRUE,'contextCompression',FALSE),1,600000
FROM ai_runtime_workflow w WHERE w.workflow_key='document-generation';

INSERT INTO ai_runtime_workflow_node(workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,
  skill_id,skill_version_id,skill_selector,input_mapping,output_mapping,node_configuration,retry_policy,timeout_ms,required_node,display_order)
SELECT wv.id,'plan-blueprint','规划文档蓝图','SKILL','AUTOMATIC','FIXED',s.id,sv.id,JSON_OBJECT(),
  JSON_OBJECT('input','$.input'),JSON_OBJECT('blueprint','$'),JSON_OBJECT('structuredOutput',TRUE),JSON_OBJECT('maxAttempts',2),90000,TRUE,10
FROM ai_runtime_workflow w JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
JOIN ai_runtime_skill s ON s.skill_key='document-blueprint-planning'
JOIN ai_runtime_skill_version sv ON sv.id=s.current_version_id AND sv.skill_id=s.id
WHERE w.workflow_key='document-generation';

INSERT INTO ai_runtime_workflow_node(workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,
  skill_selector,input_mapping,output_mapping,node_configuration,retry_policy,timeout_ms,required_node,display_order)
SELECT wv.id,n.node_key,n.name,n.node_type,n.execution_mode,'NONE',JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT(),n.configuration,JSON_OBJECT('maxAttempts',1),n.timeout_ms,TRUE,n.display_order
FROM ai_runtime_workflow w JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
JOIN (
  SELECT 'create-document-job' node_key,'创建文档任务' name,'DOCUMENT' node_type,'AUTOMATIC' execution_mode,JSON_OBJECT('action','CREATE_JOB') configuration,60000 timeout_ms,20 display_order
  UNION ALL SELECT 'confirm-outline','确认文字框架','HUMAN_CONFIRM','USER_CONFIRMATION',JSON_OBJECT('prompt','请确认文字框架。可在 revisedBlueprint 中提交调整后的蓝图。','responseSchema',JSON_OBJECT('required',JSON_ARRAY('jobKey','approved'),'properties',JSON_OBJECT('jobKey',JSON_OBJECT('type','string'),'approved',JSON_OBJECT('const',TRUE),'feedback',JSON_OBJECT('type','string'),'revisedBlueprint',JSON_OBJECT('type','object')))),NULL,30
  UNION ALL SELECT 'apply-outline','应用文字框架','DOCUMENT','AUTOMATIC',JSON_OBJECT('action','CONFIRM_OUTLINE'),60000,40
  UNION ALL SELECT 'confirm-style','确认样式预览','HUMAN_CONFIRM','USER_CONFIRMATION',JSON_OBJECT('prompt','请下载并检查 1–2 页预览，确认后继续完整生成。','responseSchema',JSON_OBJECT('required',JSON_ARRAY('jobKey','approved'),'properties',JSON_OBJECT('jobKey',JSON_OBJECT('type','string'),'approved',JSON_OBJECT('const',TRUE),'feedback',JSON_OBJECT('type','string'),'templateKey',JSON_OBJECT('type','string')))),NULL,50
  UNION ALL SELECT 'generate-final','生成完整文件','DOCUMENT','AUTOMATIC',JSON_OBJECT('action','CONFIRM_STYLE'),300000,60
  UNION ALL SELECT 'end','结束','END','AUTOMATIC',JSON_OBJECT(),NULL,999
) n
WHERE w.workflow_key='document-generation';

INSERT INTO ai_runtime_workflow_edge(workflow_version_id,from_node_id,to_node_id,edge_type,edge_configuration,priority)
SELECT wv.id,source.id,target.id,'SUCCESS',JSON_OBJECT(),0
FROM ai_runtime_workflow w JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
JOIN ai_runtime_workflow_node source ON source.workflow_version_id=wv.id
JOIN ai_runtime_workflow_node target ON target.workflow_version_id=wv.id
WHERE w.workflow_key='document-generation' AND CONCAT(source.node_key,'>',target.node_key) IN (
  'plan-blueprint>create-document-job','create-document-job>confirm-outline','confirm-outline>apply-outline',
  'apply-outline>confirm-style','confirm-style>generate-final','generate-final>end');

UPDATE ai_runtime_workflow_version v JOIN ai_runtime_workflow w ON w.id=v.workflow_id
SET v.lifecycle_status='PUBLISHED',v.entry_node_key='plan-blueprint',
  v.checksum=SHA2(CONCAT_WS(CHAR(31),w.workflow_key,v.version,CAST(v.input_schema AS CHAR),CAST(v.output_schema AS CHAR),
    CAST(v.resolver_policy AS CHAR),CAST(v.session_policy AS CHAR),v.max_parallelism,v.timeout_ms,
    (SELECT GROUP_CONCAT(CONCAT_WS(CHAR(30),n.node_key,n.node_type,n.skill_binding_type,COALESCE(n.skill_version_id,''),
      CAST(n.input_mapping AS CHAR),CAST(n.output_mapping AS CHAR),CAST(n.node_configuration AS CHAR)) ORDER BY n.display_order,n.node_key SEPARATOR '|')
      FROM ai_runtime_workflow_node n WHERE n.workflow_version_id=v.id),
    (SELECT GROUP_CONCAT(CONCAT_WS(CHAR(30),sn.node_key,tn.node_key,e.edge_type,e.priority) ORDER BY sn.node_key,e.priority,tn.node_key SEPARATOR '|')
      FROM ai_runtime_workflow_edge e JOIN ai_runtime_workflow_node sn ON sn.id=e.from_node_id
      JOIN ai_runtime_workflow_node tn ON tn.id=e.to_node_id WHERE e.workflow_version_id=v.id)),256),
  v.published_at=CURRENT_TIMESTAMP
WHERE w.workflow_key='document-generation' AND v.version='1.0.0';

UPDATE ai_runtime_workflow w JOIN ai_runtime_workflow_version v ON v.workflow_id=w.id AND v.version='1.0.0'
SET w.current_version_id=v.id WHERE w.workflow_key='document-generation';
