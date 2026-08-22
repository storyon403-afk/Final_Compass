UPDATE ai_agent_definition
SET capabilities = JSON_REMOVE(
  capabilities,
  JSON_UNQUOTE(JSON_SEARCH(capabilities, 'one', 'CREATE_SLIDES'))
)
WHERE JSON_SEARCH(capabilities, 'one', 'CREATE_SLIDES') IS NOT NULL;
