UPDATE ai_runtime_skill_version v JOIN ai_runtime_skill s ON s.id=v.skill_id
SET v.configuration=JSON_SET(v.configuration,
  '$.legacyCategory','PLANNING',
  '$.modalities',JSON_ARRAY('TEXT'))
WHERE s.skill_key='document-blueprint-planning' AND v.version='1.0.0';
