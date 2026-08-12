package cn.finalscompass.ai.runtime.mcp;

import java.net.URI;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRuntimeMcpToolBindingRepository implements RuntimeMcpToolBindingRepository {
  private final JdbcClient jdbc;

  public JdbcRuntimeMcpToolBindingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<RuntimeMcpToolBinding> findActive(String toolKey, String toolVersion) {
    if (toolKey == null || toolVersion == null || toolKey.isBlank() || toolVersion.isBlank())
      return Optional.empty();
    return jdbc.sql(
            """
            SELECT t.tool_key,v.version,s.id,s.server_key,s.name,s.transport_type,s.endpoint_uri,
              s.protocol_version,s.auth_mode,s.credential_reference,s.health_status,
              s.outbound_policy,s.configuration,b.remote_tool_name,b.pinned_schema_digest
            FROM ai_runtime_mcp_tool_binding b
            JOIN ai_runtime_tool_version v ON v.id=b.tool_version_id
            JOIN ai_runtime_tool t ON t.id=v.tool_id
            JOIN ai_runtime_mcp_server s ON s.id=b.server_id
            WHERE t.tool_key=:toolKey AND v.version=:toolVersion AND t.status='ACTIVE'
              AND v.lifecycle_status='PUBLISHED' AND b.status='ACTIVE' AND s.status='ACTIVE'
            """)
        .param("toolKey", toolKey)
        .param("toolVersion", toolVersion)
        .query(
            (result, row) -> {
              RuntimeMcpTransportType transport =
                  RuntimeMcpTransportType.valueOf(result.getString("transport_type"));
              String endpoint = result.getString("endpoint_uri");
              validateEndpoint(transport, endpoint);
              RuntimeMcpServerDefinition server =
                  new RuntimeMcpServerDefinition(
                      result.getLong("id"),
                      result.getString("server_key"),
                      result.getString("name"),
                      transport,
                      endpoint,
                      result.getString("protocol_version"),
                      RuntimeMcpAuthMode.valueOf(result.getString("auth_mode")),
                      result.getString("credential_reference"),
                      RuntimeMcpHealthStatus.valueOf(result.getString("health_status")),
                      result.getString("outbound_policy"),
                      result.getString("configuration"));
              return new RuntimeMcpToolBinding(
                  result.getString("tool_key"),
                  result.getString("version"),
                  server,
                  result.getString("remote_tool_name"),
                  result.getString("pinned_schema_digest"));
            })
        .optional();
  }

  private void validateEndpoint(RuntimeMcpTransportType transport, String value) {
    if (transport == RuntimeMcpTransportType.STDIO) {
      if (value != null)
        throw new IllegalStateException("STDIO MCP Server cannot have endpoint URI");
      return;
    }
    URI uri = URI.create(value);
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null)
      throw new IllegalStateException("MCP Server endpoint is invalid");
  }
}
