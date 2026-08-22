UPDATE ai_agent_definition
SET name='Final Compass 内置文档 Agent',gateway_url='internal://document-agent',status='ACTIVE',approval_policy='REQUIRE_APPROVAL'
WHERE agent_key='default-external-agent';
