-- Default web search tools for builtin agents (merge, do not overwrite custom configs).

UPDATE raglaw_agent_config
SET tools_json = '["rag_search","tavily-search"]',
    mcp_servers_json = '["tavily"]'
WHERE code = 'GENERAL'
  AND (
    tools_json IS NULL
    OR tools_json = '[]'
    OR NOT JSON_CONTAINS(tools_json, '"tavily-search"')
  );

UPDATE raglaw_agent_config
SET tools_json = JSON_ARRAY_APPEND(COALESCE(tools_json, '[]'), '$', 'tavily-search'),
    mcp_servers_json = CASE
        WHEN mcp_servers_json IS NULL OR mcp_servers_json = '[]' THEN '["tavily"]'
        WHEN JSON_CONTAINS(mcp_servers_json, '"tavily"') THEN mcp_servers_json
        ELSE JSON_ARRAY_APPEND(mcp_servers_json, '$', 'tavily')
    END
WHERE code IN ('STATUTE', 'CASE', 'CONTRACT')
  AND NOT JSON_CONTAINS(COALESCE(tools_json, '[]'), '"tavily-search"');
