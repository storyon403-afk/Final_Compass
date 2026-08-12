UPDATE ai_runtime_workflow_version v JOIN ai_runtime_workflow w ON w.id=v.workflow_id
SET v.input_schema=JSON_OBJECT('type','object','properties',JSON_OBJECT('input',JSON_OBJECT('type','string')),'required',JSON_ARRAY('input'),'additionalProperties',TRUE),
    v.output_schema=JSON_OBJECT('type','object','properties',JSON_OBJECT('passed',JSON_OBJECT('type','boolean'),'artifact',JSON_OBJECT('type','object')),'required',JSON_ARRAY('passed','artifact'),'additionalProperties',TRUE)
WHERE w.workflow_key='minimal-zine-poster' AND v.version='0.1.0';

UPDATE ai_runtime_skill_version v JOIN ai_runtime_skill s ON s.id=v.skill_id
SET v.input_schema=JSON_OBJECT('type','object','properties',JSON_OBJECT(),'required',JSON_ARRAY(),'additionalProperties',TRUE),
    v.output_schema=JSON_OBJECT('type','object','properties',JSON_OBJECT(),'required',JSON_ARRAY(),'additionalProperties',TRUE)
WHERE s.skill_key IN ('minimal-zine-poster-generation','minimal-zine-poster-review') AND v.version='0.1.0';
