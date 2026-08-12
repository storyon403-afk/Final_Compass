-- 新增"直接回答"技能与"普通问答"工作流：
-- 普通文本提问直接得到文本回答，与确认式文档生成、海报生成流程彻底分离。

INSERT INTO ai_runtime_skill(skill_key,name,skill_type,description,status,risk_level,domain_tags) VALUES
  ('direct-answer','直接回答','REASONING','针对普通文本提问直接给出完整、清晰的回答；不生成任何文件产物，不进入确认式文档流程。','ACTIVE','LOW',JSON_ARRAY('GENERAL','KNOWLEDGE'));

INSERT INTO ai_runtime_skill_version(
  skill_id,version,lifecycle_status,executor_type,executor_key,input_schema,output_schema,
  prompt_template,output_contract,required_capabilities,permission_policy,allowed_tools,
  configuration,max_input_units,timeout_ms,retry_policy,checksum,published_at
)
SELECT s.id,'1.0.0','PUBLISHED','LLM_PROMPT','provider-prompt-v1',
  JSON_OBJECT(
    'type','object',
    'properties',JSON_OBJECT(
      'input',JSON_OBJECT('type','string','minLength',1),
      'context',JSON_OBJECT('type','object')),
    'required',JSON_ARRAY('input'),
    'additionalProperties',FALSE),
  JSON_OBJECT(
    'type','object',
    'properties',JSON_OBJECT('content',JSON_OBJECT('type','string')),
    'required',JSON_ARRAY('content'),
    'additionalProperties',FALSE),
'数学公式输出规范：

1. 行内数学公式必须使用：
$公式$

示例：
$f(x)=x^2$

2. 独立数学公式必须使用：
$$公式$$

示例：
$$
\\int_0^1 f(x)dx
$$

3. 禁止直接输出裸 LaTeX：
\\int
\\lim
\\sum
\\frac

所有数学符号、公式推导必须被 Markdown LaTeX 标记包裹。

你是直接回答助手。针对用户的问题，直接、完整、清晰地给出答案。

要求：
1. 直接回答问题本身，不要只做提示或反问，不要刻意隐藏结论。
2. 先给出明确结论，再补充必要的解释、推导或例子。
3. 使用 Markdown 组织内容；除非用户明确要求，否则不要生成文档大纲、章节框架或文件产物。
4. 信息不足时，先基于问题给出最可能的解答，再简短说明需要补充的信息。',
'直接输出完整回答正文（Markdown），包含明确结论与必要解释；不要输出分步提示或引导式提问。',
  JSON_ARRAY('TEXT_REASONING'),JSON_OBJECT('authenticated',TRUE,'permissions',JSON_ARRAY()),JSON_ARRAY(),
  JSON_OBJECT('templateFormat','LEGACY_V2','migrationSource','AiSkillConfiguration',
    'legacyCategory','LEARNING','modalities',JSON_ARRAY('TEXT')),
  8000,60000,JSON_OBJECT('maxAttempts',1),REPEAT('0',64),CURRENT_TIMESTAMP
FROM ai_runtime_skill s WHERE s.skill_key='direct-answer';

UPDATE ai_runtime_skill_version v
JOIN ai_runtime_skill s ON s.id=v.skill_id
SET v.checksum=SHA2(CONCAT_WS(CHAR(31),
  s.skill_key,v.version,v.executor_type,v.executor_key,
  CAST(v.input_schema AS CHAR),CAST(v.output_schema AS CHAR),v.prompt_template,v.output_contract,
  CAST(v.required_capabilities AS CHAR),CAST(v.permission_policy AS CHAR),CAST(v.allowed_tools AS CHAR),
  CAST(v.configuration AS CHAR),v.max_input_units,v.timeout_ms,CAST(v.retry_policy AS CHAR)
),256)
WHERE s.skill_key='direct-answer' AND v.version='1.0.0';

UPDATE ai_runtime_skill s
JOIN ai_runtime_skill_version v ON v.skill_id=s.id AND v.version='1.0.0'
SET s.current_version_id=v.id
WHERE s.skill_key='direct-answer';

INSERT INTO ai_runtime_workflow(workflow_key,name,description,status,runtime_type,resolver_tags) VALUES
  ('direct-qa','普通问答','普通文本问答：直接回答问题，不生成文件产物，不进入确认式文档生成流程。','ACTIVE','WORKFLOW',
   JSON_ARRAY('GENERAL','QUESTION_ANSWERING'));

INSERT INTO ai_runtime_workflow_version(
  workflow_id,version,lifecycle_status,input_schema,output_schema,resolver_policy,
  session_policy,max_parallelism,timeout_ms
)
SELECT w.id,'1.0.0','DRAFT',
  JSON_OBJECT(
    'type','object',
    'properties',JSON_OBJECT(
      'input',JSON_OBJECT('type','string','minLength',1),
      'context',JSON_OBJECT('type','object')),
    'required',JSON_ARRAY('input'),
    'additionalProperties',FALSE),
  JSON_OBJECT(
    'type','object',
    'properties',JSON_OBJECT('content',JSON_OBJECT('type','string')),
    'required',JSON_ARRAY('content'),
    'additionalProperties',FALSE),
  JSON_OBJECT(
    'strategy','LEARNING_TASK_TYPE',
    'learningTaskType','GENERAL_TASK',
    'taskTypes',JSON_ARRAY('GENERAL_TASK','QUESTION_ANSWERING','CONCEPT_EXPLANATION','KNOWLEDGE_QA'),
    'outputArtifacts',JSON_ARRAY('TEXT')),
  JSON_OBJECT(
    'memoryType','SESSION',
    'releaseOnTerminalState',TRUE,
    'contextCompression',FALSE),
  1,300000
FROM ai_runtime_workflow w WHERE w.workflow_key='direct-qa';

INSERT INTO ai_runtime_workflow_node(
  workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,
  skill_id,skill_version_id,skill_selector,input_mapping,output_mapping,
  node_configuration,retry_policy,timeout_ms,required_node,display_order
)
SELECT wv.id,'direct-answer',s.name,'SKILL','AUTOMATIC','FIXED',s.id,sv.id,
  JSON_OBJECT(),
  JSON_OBJECT('taskInput','$.input','context','$.context','previousOutput',NULL),
  JSON_OBJECT('content','$.content'),
  JSON_OBJECT('legacyStepOrder',1,'requiredContext',JSON_ARRAY(),'migrationSource','WorkflowConfiguration'),
  JSON_OBJECT('maxAttempts',1),60000,TRUE,1
FROM ai_runtime_workflow w
JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
JOIN ai_runtime_skill s ON s.skill_key='direct-answer' AND s.status='ACTIVE'
JOIN ai_runtime_skill_version sv
  ON sv.id=s.current_version_id AND sv.skill_id=s.id AND sv.lifecycle_status='PUBLISHED'
WHERE w.workflow_key='direct-qa';

INSERT INTO ai_runtime_workflow_node(
  workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,
  skill_selector,input_mapping,output_mapping,node_configuration,retry_policy,
  required_node,display_order
)
SELECT wv.id,'end','结束','END','AUTOMATIC','NONE',JSON_OBJECT(),
  JSON_OBJECT('content','$.previous.content'),JSON_OBJECT('content','$.content'),
  JSON_OBJECT('migrationSource','WorkflowConfiguration'),JSON_OBJECT('maxAttempts',1),TRUE,999
FROM ai_runtime_workflow w
JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
WHERE w.workflow_key='direct-qa';

INSERT INTO ai_runtime_workflow_edge(
  workflow_version_id,from_node_id,to_node_id,edge_type,edge_configuration,priority
)
SELECT wv.id,source_node.id,target_node.id,'SUCCESS',JSON_OBJECT(),0
FROM ai_runtime_workflow w
JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
JOIN ai_runtime_workflow_node source_node
  ON source_node.workflow_version_id=wv.id AND source_node.node_key='direct-answer'
JOIN ai_runtime_workflow_node target_node
  ON target_node.workflow_version_id=wv.id AND target_node.node_key='end'
WHERE w.workflow_key='direct-qa';

UPDATE ai_runtime_workflow_version v
JOIN ai_runtime_workflow w ON w.id=v.workflow_id
SET v.lifecycle_status='PUBLISHED',
  v.entry_node_key='direct-answer',
  v.checksum=SHA2(CONCAT_WS(CHAR(31),
    w.workflow_key,v.version,CAST(v.input_schema AS CHAR),CAST(v.output_schema AS CHAR),
    CAST(v.resolver_policy AS CHAR),CAST(v.session_policy AS CHAR),v.max_parallelism,v.timeout_ms,
    (SELECT GROUP_CONCAT(CONCAT_WS(CHAR(30),n.node_key,n.node_type,n.skill_binding_type,
      COALESCE(n.skill_version_id,''),CAST(n.input_mapping AS CHAR),CAST(n.output_mapping AS CHAR),
      CAST(n.node_configuration AS CHAR)) ORDER BY n.display_order,n.node_key SEPARATOR '|')
     FROM ai_runtime_workflow_node n WHERE n.workflow_version_id=v.id),
    (SELECT GROUP_CONCAT(CONCAT_WS(CHAR(30),source_node.node_key,target_node.node_key,e.edge_type,e.priority)
      ORDER BY source_node.node_key,e.priority,target_node.node_key SEPARATOR '|')
     FROM ai_runtime_workflow_edge e
     JOIN ai_runtime_workflow_node source_node ON source_node.id=e.from_node_id
     JOIN ai_runtime_workflow_node target_node ON target_node.id=e.to_node_id
     WHERE e.workflow_version_id=v.id)
  ),256),
  v.published_at=CURRENT_TIMESTAMP
WHERE w.workflow_key='direct-qa' AND v.version='1.0.0';

UPDATE ai_runtime_workflow w
JOIN ai_runtime_workflow_version v ON v.workflow_id=w.id AND v.version='1.0.0'
SET w.current_version_id=v.id
WHERE w.workflow_key='direct-qa';
