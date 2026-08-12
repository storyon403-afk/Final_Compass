CREATE TABLE ai_image_artifact(
  id BIGINT AUTO_INCREMENT PRIMARY KEY,artifact_key CHAR(36) NOT NULL UNIQUE,user_id BIGINT NOT NULL,
  execution_id BIGINT NOT NULL,skill_key VARCHAR(100) NOT NULL,storage_name VARCHAR(255) NOT NULL UNIQUE,
  original_name VARCHAR(255) NOT NULL,content_type VARCHAR(100) NOT NULL,size_bytes BIGINT NOT NULL,
  width_px INT NOT NULL,height_px INT NOT NULL,content_digest CHAR(64) NOT NULL,metadata JSON NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),INDEX idx_image_artifact_user(user_id,created_at)
);

INSERT INTO ai_runtime_skill(skill_key,name,skill_type,description,status,risk_level,domain_tags) VALUES
('minimal-zine-poster-planning','Minimal Zine 海报规划','PLANNING','将主题或内容简报编译为结构化极简 Zine 海报蓝图和图片 Prompt，不生成图片。','ACTIVE','LOW',JSON_ARRAY('POSTER','IMAGE','ZINE','PLANNING')),
('minimal-zine-poster-generation','Minimal Zine 海报生成','GENERATION','根据确认后的海报蓝图生成竖版 PNG 图片并登记 Artifact。','ACTIVE','MEDIUM',JSON_ARRAY('POSTER','IMAGE','ZINE','GENERATION')),
('minimal-zine-poster-review','Minimal Zine 海报检查','REASONING','检查生成产物和风格质量门禁，不产生外部副作用。','ACTIVE','LOW',JSON_ARRAY('POSTER','IMAGE','ZINE','REVIEW'));

INSERT INTO ai_runtime_skill_version(skill_id,version,lifecycle_status,executor_type,executor_key,input_schema,output_schema,prompt_template,output_contract,required_capabilities,permission_policy,allowed_tools,configuration,max_input_units,timeout_ms,retry_policy,checksum,published_at)
SELECT id,'0.1.0','PUBLISHED','LLM_PROMPT','provider-prompt-v1',
JSON_OBJECT('type','object','properties',JSON_OBJECT('input',JSON_OBJECT('type','string')),'required',JSON_ARRAY('input')),
JSON_OBJECT('type','object','required',JSON_ARRAY('title','prompt','negativePrompt','recipe'),'properties',JSON_OBJECT('title',JSON_OBJECT('type','string'),'mood',JSON_OBJECT('type','string'),'accentColor',JSON_OBJECT('type','string'),'prompt',JSON_OBJECT('type','string'),'negativePrompt',JSON_OBJECT('type','string'),'recipe',JSON_OBJECT('type','object'))),
'你是 Minimal Zine Poster Planning Skill。把用户主题提炼成一个可成像隐喻。必须使用竖版3:5旧纸画布、70%-90%留白、8%-25%小型视觉簇、衬线或打字机字体、一个缩略图可见的高饱和色锚点、扫描纸张与复印/孔版印刷缺陷。选择 layout、anchor、typography、accent、texture、mood。避免广告、Logo、CTA、全幅场景、3D、霓虹、影视灯光、卡通、密集拼贴和长文本。只输出符合 Schema 的 JSON。',
'输出 Poster Blueprint JSON。','["TEXT_REASONING"]',JSON_OBJECT('authenticated',TRUE,'permissions',JSON_ARRAY()),JSON_ARRAY(),
JSON_OBJECT('legacyCategory','PLANNING','modalities',JSON_ARRAY('TEXT'),'sourceRepository','https://github.com/LiamGvchi/gc-minimal-zine-poster','sourceSkill','gc-minimal-zine-poster-v0-1','license','MIT','mode','STANDARD'),20000,90000,JSON_OBJECT('maxAttempts',2),REPEAT('1',64),CURRENT_TIMESTAMP
FROM ai_runtime_skill WHERE skill_key='minimal-zine-poster-planning';

INSERT INTO ai_runtime_skill_version(skill_id,version,lifecycle_status,executor_type,executor_key,input_schema,output_schema,prompt_template,output_contract,required_capabilities,permission_policy,allowed_tools,configuration,max_input_units,timeout_ms,retry_policy,checksum,published_at)
SELECT id,'0.1.0','PUBLISHED','INTERNAL','minimal-zine-poster-renderer-v1',JSON_OBJECT('type','object'),JSON_OBJECT('type','object'),NULL,'输出图片 Artifact JSON。',JSON_ARRAY(),JSON_OBJECT('authenticated',TRUE,'permissions',JSON_ARRAY('FILE_WRITE')),JSON_ARRAY(),JSON_OBJECT('legacyCategory','GENERATION','modalities',JSON_ARRAY('TEXT'),'artifactType','PNG','sourceRepository','https://github.com/LiamGvchi/gc-minimal-zine-poster','license','MIT'),30000,60000,JSON_OBJECT('maxAttempts',1),REPEAT('2',64),CURRENT_TIMESTAMP FROM ai_runtime_skill WHERE skill_key='minimal-zine-poster-generation';

INSERT INTO ai_runtime_skill_version(skill_id,version,lifecycle_status,executor_type,executor_key,input_schema,output_schema,prompt_template,output_contract,required_capabilities,permission_policy,allowed_tools,configuration,max_input_units,timeout_ms,retry_policy,checksum,published_at)
SELECT id,'0.1.0','PUBLISHED','INTERNAL','minimal-zine-poster-review-v1',JSON_OBJECT('type','object'),JSON_OBJECT('type','object'),NULL,'输出质量门禁 JSON。',JSON_ARRAY(),JSON_OBJECT('authenticated',TRUE,'permissions',JSON_ARRAY()),JSON_ARRAY(),JSON_OBJECT('legacyCategory','REASONING','modalities',JSON_ARRAY('TEXT'),'qualityGate','MINIMAL_ZINE_V0_1'),30000,10000,JSON_OBJECT('maxAttempts',1),REPEAT('3',64),CURRENT_TIMESTAMP FROM ai_runtime_skill WHERE skill_key='minimal-zine-poster-review';

UPDATE ai_runtime_skill s JOIN ai_runtime_skill_version v ON v.skill_id=s.id AND v.version='0.1.0' SET s.current_version_id=v.id WHERE s.skill_key LIKE 'minimal-zine-poster-%';

INSERT INTO ai_runtime_workflow(workflow_key,name,description,status,runtime_type,resolver_tags) VALUES('minimal-zine-poster','Minimal Zine 海报生成','将自然语言主题规划为 Minimal Zine 海报，生成 PNG Artifact 并执行质量检查。','ACTIVE','WORKFLOW',JSON_ARRAY('POSTER','IMAGE_GENERATION','ZINE','MINIMAL_ZINE'));
INSERT INTO ai_runtime_workflow_version(workflow_id,version,lifecycle_status,input_schema,output_schema,resolver_policy,session_policy,max_parallelism,timeout_ms)
SELECT id,'0.1.0','DRAFT',JSON_OBJECT('type','object'),JSON_OBJECT('type','object'),JSON_OBJECT('strategy','DYNAMIC','taskTypes',JSON_ARRAY('POSTER_GENERATION'),'outputArtifacts',JSON_ARRAY('PNG')),JSON_OBJECT('memoryType','SESSION','releaseOnTerminalState',TRUE),1,180000 FROM ai_runtime_workflow WHERE workflow_key='minimal-zine-poster';

INSERT INTO ai_runtime_workflow_node(workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,skill_id,skill_version_id,skill_selector,input_mapping,output_mapping,node_configuration,retry_policy,timeout_ms,required_node,display_order)
SELECT wv.id,s.skill_key,s.name,'SKILL','AUTOMATIC','FIXED',s.id,s.current_version_id,JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT('source','gc-minimal-zine-poster'),JSON_OBJECT('maxAttempts',1),60000,TRUE,x.ord
FROM ai_runtime_workflow w JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id JOIN (SELECT 'minimal-zine-poster-planning' k,10 ord UNION ALL SELECT 'minimal-zine-poster-generation',20 UNION ALL SELECT 'minimal-zine-poster-review',30)x JOIN ai_runtime_skill s ON s.skill_key=x.k WHERE w.workflow_key='minimal-zine-poster';
INSERT INTO ai_runtime_workflow_node(workflow_version_id,node_key,name,node_type,execution_mode,skill_binding_type,skill_selector,input_mapping,output_mapping,node_configuration,retry_policy,required_node,display_order)
SELECT wv.id,'end','结束','END','AUTOMATIC','NONE',JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT(),JSON_OBJECT('maxAttempts',1),TRUE,999 FROM ai_runtime_workflow w JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id WHERE w.workflow_key='minimal-zine-poster';
INSERT INTO ai_runtime_workflow_edge(workflow_version_id,from_node_id,to_node_id,edge_type,edge_configuration,priority)
SELECT wv.id,a.id,b.id,'SUCCESS',JSON_OBJECT(),0 FROM ai_runtime_workflow w JOIN ai_runtime_workflow_version wv ON wv.workflow_id=w.id JOIN ai_runtime_workflow_node a ON a.workflow_version_id=wv.id JOIN ai_runtime_workflow_node b ON b.workflow_version_id=wv.id WHERE w.workflow_key='minimal-zine-poster' AND CONCAT(a.node_key,'>',b.node_key) IN ('minimal-zine-poster-planning>minimal-zine-poster-generation','minimal-zine-poster-generation>minimal-zine-poster-review','minimal-zine-poster-review>end');
UPDATE ai_runtime_workflow_version v JOIN ai_runtime_workflow w ON w.id=v.workflow_id SET v.lifecycle_status='PUBLISHED',v.entry_node_key='minimal-zine-poster-planning',v.checksum=SHA2(CONCAT(w.workflow_key,v.version),256),v.published_at=CURRENT_TIMESTAMP WHERE w.workflow_key='minimal-zine-poster';
UPDATE ai_runtime_workflow w JOIN ai_runtime_workflow_version v ON v.workflow_id=w.id SET w.current_version_id=v.id WHERE w.workflow_key='minimal-zine-poster';
