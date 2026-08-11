package cn.finalscompass.ai.runtime.mcp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Optional;

@Repository
public class JdbcRuntimeMcpServerRepository implements RuntimeMcpServerRepository {
    private final JdbcClient jdbc;

    public JdbcRuntimeMcpServerRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public Optional<RuntimeMcpServerDefinition> findActiveByKey(String serverKey) {
        if (serverKey == null || !serverKey.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*"))
            return Optional.empty();
        return jdbc.sql("""
                SELECT id,server_key,name,transport_type,endpoint_uri,protocol_version,auth_mode,
                  credential_reference,health_status,outbound_policy,configuration
                FROM ai_runtime_mcp_server WHERE server_key=:serverKey AND status='ACTIVE'
                """).param("serverKey", serverKey).query((result, row) -> {
                    RuntimeMcpTransportType transport = RuntimeMcpTransportType.valueOf(
                            result.getString("transport_type"));
                    String endpoint = result.getString("endpoint_uri");
                    if (transport == RuntimeMcpTransportType.STREAMABLE_HTTP) validateEndpoint(endpoint);
                    else if (endpoint != null) throw new IllegalStateException("STDIO MCP endpoint is invalid");
                    return new RuntimeMcpServerDefinition(result.getLong("id"),
                            result.getString("server_key"), result.getString("name"), transport, endpoint,
                            result.getString("protocol_version"),
                            RuntimeMcpAuthMode.valueOf(result.getString("auth_mode")),
                            result.getString("credential_reference"),
                            RuntimeMcpHealthStatus.valueOf(result.getString("health_status")),
                            result.getString("outbound_policy"), result.getString("configuration"));
                }).optional();
    }

    private void validateEndpoint(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalStateException("MCP endpoint is invalid");
    }
}
