CREATE TEMPORARY TABLE ai_runtime_workflow_seed (
  workflow_key VARCHAR(100) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  learning_task_type VARCHAR(64) NOT NULL
);

CREATE TEMPORARY TABLE ai_runtime_workflow_step_seed (
  workflow_key VARCHAR(100) NOT NULL,
  step_order INT NOT NULL,
  skill_key VARCHAR(100) NOT NULL,
  required_context JSON NOT NULL,
  next_node_key VARCHAR(100) NOT NULL,
  PRIMARY KEY (workflow_key, step_order),
  UNIQUE (workflow_key, skill_key)
);

INSERT INTO ai_runtime_workflow_seed VALUES
  ('exam-preparation','考试复习规划','分析课程资料与教师信息，生成可执行的考试复习计划。','EXAM_PREPARATION'),
  ('material-analysis','课程资料分析','总结课程资料并整合为结构化学习成果。','MATERIAL_ANALYSIS'),
  ('question-assistance','问题分步提示','针对学习问题提供循序渐进的提示。','QUESTION_ASSISTANCE'),
  ('answer-review','学生解答检查','检查学生解答并定位首个可确认错误。','ANSWER_REVIEW'),
  ('study-planning','学习计划制定','根据课程目标生成现实可执行的学习计划。','STUDY_PLANNING');

INSERT INTO ai_runtime_workflow_step_seed VALUES
  ('exam-preparation',1,'material-summary',JSON_ARRAY('COURSE','MATERIALS'),'exam-focus-analysis'),
  ('exam-preparation',2,'exam-focus-analysis',JSON_ARRAY('COURSE','TEACHER'),'study-plan-generation'),
  ('exam-preparation',3,'study-plan-generation',JSON_ARRAY('COURSE'),'end'),
  ('material-analysis',1,'material-summary',JSON_ARRAY('MATERIALS'),'learning-result-synthesis'),
  ('material-analysis',2,'learning-result-synthesis',JSON_ARRAY('COURSE'),'end'),
  ('question-assistance',1,'progressive-hint',JSON_ARRAY(),'end'),
  ('answer-review',1,'solution-review',JSON_ARRAY(),'end'),
  ('study-planning',1,'study-plan-generation',JSON_ARRAY('COURSE'),'end');

INSERT INTO ai_runtime_workflow(
  workflow_key,name,description,status,runtime_type,resolver_tags
)
SELECT workflow_key,name,description,'ACTIVE','WORKFLOW',
  JSON_ARRAY('LEARNING',learning_task_type)
FROM ai_runtime_workflow_seed;

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
    'learningTaskType',d.learning_task_type,
    'migrationSource','WorkflowConfiguration'),
  JSON_OBJECT(
    'memoryType','SESSION',
    'releaseOnTerminalState',TRUE,
    'contextCompression',FALSE),
  1,300000
FROM ai_runtime_workflow w
JOIN ai_runtime_workflow_seed d ON d.workflow_key=w.workflow_key;

INSERT INTO ai_runtime_workflow_node(
  workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,
  skill_id,skill_version_id,skill_selector,input_mapping,output_mapping,
  node_configuration,retry_policy,timeout_ms,required_node,display_order
)
SELECT wv.id,d.skill_key,s.name,'SKILL','AUTOMATIC','FIXED',s.id,sv.id,
  JSON_OBJECT(),
  JSON_OBJECT(
    'taskInput','$.input',
    'context','$.context',
    'previousOutput',CASE WHEN d.step_order=1 THEN NULL ELSE '$.previous.content' END),
  JSON_OBJECT('content','$.content'),
  JSON_OBJECT(
    'legacyStepOrder',d.step_order,
    'requiredContext',d.required_context,
    'migrationSource','WorkflowConfiguration'),
  JSON_OBJECT('maxAttempts',1),60000,TRUE,d.step_order
FROM ai_runtime_workflow_step_seed d
JOIN ai_runtime_workflow w ON w.workflow_key=d.workflow_key
JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
JOIN ai_runtime_skill s ON s.skill_key=d.skill_key AND s.status='ACTIVE'
JOIN ai_runtime_skill_version sv
  ON sv.id=s.current_version_id AND sv.skill_id=s.id AND sv.lifecycle_status='PUBLISHED';

INSERT INTO ai_runtime_workflow_node(
  workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,
  skill_selector,input_mapping,output_mapping,node_configuration,retry_policy,
  required_node,display_order
)
SELECT wv.id,'end','结束','END','AUTOMATIC','NONE',JSON_OBJECT(),
  JSON_OBJECT('content','$.previous.content'),JSON_OBJECT('content','$.content'),
  JSON_OBJECT('migrationSource','WorkflowConfiguration'),JSON_OBJECT('maxAttempts',1),TRUE,999
FROM ai_runtime_workflow w
JOIN ai_runtime_workflow_seed d ON d.workflow_key=w.workflow_key
JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0';

INSERT INTO ai_runtime_workflow_edge(
  workflow_version_id,from_node_id,to_node_id,edge_type,edge_configuration,priority
)
SELECT wv.id,source_node.id,target_node.id,'SUCCESS',JSON_OBJECT(),0
FROM ai_runtime_workflow_step_seed source_step
JOIN ai_runtime_workflow w ON w.workflow_key=source_step.workflow_key
JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id AND wv.version='1.0.0'
JOIN ai_runtime_workflow_node source_node
  ON source_node.workflow_version_id=wv.id AND source_node.node_key=source_step.skill_key
JOIN ai_runtime_workflow_node target_node
  ON target_node.workflow_version_id=wv.id AND target_node.node_key=source_step.next_node_key;

UPDATE ai_runtime_workflow_version v
JOIN ai_runtime_workflow w ON w.id=v.workflow_id
JOIN ai_runtime_workflow_seed d ON d.workflow_key=w.workflow_key
JOIN ai_runtime_workflow_step_seed first_step
  ON first_step.workflow_key=w.workflow_key AND first_step.step_order=1
SET v.lifecycle_status='PUBLISHED',
  v.entry_node_key=first_step.skill_key,
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
WHERE v.version='1.0.0';

UPDATE ai_runtime_workflow w
JOIN ai_runtime_workflow_version v ON v.workflow_id=w.id AND v.version='1.0.0'
JOIN ai_runtime_workflow_seed d ON d.workflow_key=w.workflow_key
SET w.current_version_id=v.id;

DROP TEMPORARY TABLE ai_runtime_workflow_step_seed;
DROP TEMPORARY TABLE ai_runtime_workflow_seed;
