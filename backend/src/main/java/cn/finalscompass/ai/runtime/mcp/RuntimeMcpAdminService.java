package cn.finalscompass.ai.runtime.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class RuntimeMcpAdminService {
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final RuntimeMcpDiscoveryService discovery;
  private final ObjectMapper json;

  public RuntimeMcpAdminService(
      JdbcClient jdbc,
      TransactionTemplate transactions,
      RuntimeMcpDiscoveryService discovery,
      ObjectMapper json) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.discovery = discovery;
    this.json = json;
  }

  public Map<String, Object> overview() {
    return Map.of(
        "servers",
        jdbc.sql(
                """
SELECT s.id,s.server_key,s.name,s.transport_type,s.endpoint_uri,s.protocol_version,s.auth_mode,
  s.status,s.health_status,s.last_discovered_at,
  EXISTS(SELECT 1 FROM ai_runtime_mcp_oauth_connection o
    WHERE o.server_id=s.id AND o.status='CONNECTED') oauth_connected
FROM ai_runtime_mcp_server s ORDER BY s.server_key
""")
            .query()
            .listOfRows(),
        "discoveredTools",
        jdbc.sql(
                """
SELECT d.id,s.server_key,d.remote_tool_name,d.title,d.description,d.input_schema,
  d.output_schema,d.schema_digest
FROM ai_runtime_mcp_discovered_tool d
JOIN ai_runtime_mcp_discovery_snapshot x ON x.id=d.discovery_snapshot_id AND x.status='CURRENT'
JOIN ai_runtime_mcp_server s ON s.id=x.server_id ORDER BY s.server_key,d.remote_tool_name
""")
            .query()
            .listOfRows(),
        "approvals",
        jdbc.sql(
                """
SELECT a.id,s.server_key,d.remote_tool_name,a.target_tool_key,a.target_version,a.risk_level,
  a.required_permissions,a.decision,a.review_note,a.created_at,a.reviewed_at
FROM ai_runtime_mcp_approval a JOIN ai_runtime_mcp_server s ON s.id=a.server_id
JOIN ai_runtime_mcp_discovered_tool d ON d.id=a.discovered_tool_id
ORDER BY FIELD(a.decision,'PENDING','APPROVED','REJECTED'),a.created_at DESC
""")
            .query()
            .listOfRows());
  }

  public RuntimeMcpDiscoveryReport discover(String serverKey, long adminId) {
    return discovery.discover(serverKey, adminId);
  }

  public void saveServer(ServerInput input, long adminId) {
    if (input == null
        || input.serverKey() == null
        || !input.serverKey().matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
        || input.name() == null
        || input.name().isBlank()
        || !Set.of("STREAMABLE_HTTP", "STDIO").contains(input.transportType())
        || !Set.of("NONE", "PLATFORM_OAUTH", "USER_OAUTH", "SERVICE_TOKEN")
            .contains(input.authMode()))
      throw new IllegalArgumentException("MCP Server configuration is invalid");
    if ("STREAMABLE_HTTP".equals(input.transportType())) validateHttps(input.endpointUri());
    if (input.authMode().endsWith("OAUTH")) {
      validateHttps(input.authorizationEndpoint());
      validateHttps(input.tokenEndpoint());
      if (input.clientId() == null
          || input.clientId().isBlank()
          || input.scopes() == null
          || input.scopes().isBlank())
        throw new IllegalArgumentException("MCP OAuth configuration incomplete");
    }
    String command = input.stdioCommand() == null ? null : write(input.stdioCommand());
    jdbc.sql(
            """
INSERT INTO ai_runtime_mcp_server(server_key,name,description,transport_type,endpoint_uri,
  protocol_version,auth_mode,credential_reference,oauth_authorization_endpoint,
  oauth_token_endpoint,oauth_client_id,oauth_scopes,stdio_command,stdio_working_directory,
  status,health_status,outbound_policy,configuration,created_by,updated_by)
VALUES (:key,:name,:description,:transport,:endpoint,'2025-06-18',:auth,:credential,
  :authorize,:token,:client,:scopes,CASE WHEN :command IS NULL THEN NULL ELSE CAST(:command AS JSON) END,
  :workingDirectory,:status,'UNKNOWN',CAST(:policy AS JSON),JSON_OBJECT(),:admin,:admin)
ON DUPLICATE KEY UPDATE name=:name,description=:description,transport_type=:transport,
  endpoint_uri=:endpoint,auth_mode=:auth,credential_reference=:credential,
  oauth_authorization_endpoint=:authorize,oauth_token_endpoint=:token,oauth_client_id=:client,
  oauth_scopes=:scopes,stdio_command=CASE WHEN :command IS NULL THEN NULL ELSE CAST(:command AS JSON) END,
  stdio_working_directory=:workingDirectory,status=:status,outbound_policy=CAST(:policy AS JSON),
  health_status='UNKNOWN',updated_by=:admin
""")
        .param("key", input.serverKey())
        .param("name", limited(input.name(), 160))
        .param("description", limited(input.description() == null ? "" : input.description(), 1000))
        .param("transport", input.transportType())
        .param("endpoint", blank(input.endpointUri()))
        .param("auth", input.authMode())
        .param("credential", blank(input.credentialReference()))
        .param("authorize", blank(input.authorizationEndpoint()))
        .param("token", blank(input.tokenEndpoint()))
        .param("client", blank(input.clientId()))
        .param("scopes", blank(input.scopes()))
        .param("command", command)
        .param("workingDirectory", blank(input.stdioWorkingDirectory()))
        .param("status", input.enabled() ? "ACTIVE" : "DISABLED")
        .param(
            "policy",
            write(
                Map.of(
                    "allowedHosts",
                    input.allowedHosts() == null ? List.of() : input.allowedHosts())))
        .param("admin", adminId)
        .update();
  }

  public List<Map<String, Object>> diff(String serverKey) {
    List<Long> snapshots =
        jdbc.sql(
                """
                SELECT d.id FROM ai_runtime_mcp_discovery_snapshot d
                JOIN ai_runtime_mcp_server s ON s.id=d.server_id
                WHERE s.server_key=:key AND d.status IN ('CURRENT','SUPERSEDED')
                ORDER BY d.discovered_at DESC LIMIT 2
                """)
            .param("key", serverKey)
            .query(Long.class)
            .list();
    if (snapshots.isEmpty()) return List.of();
    Map<String, String> current = toolDigests(snapshots.getFirst());
    Map<String, String> previous = snapshots.size() > 1 ? toolDigests(snapshots.get(1)) : Map.of();
    Set<String> names = new java.util.TreeSet<>();
    names.addAll(current.keySet());
    names.addAll(previous.keySet());
    List<Map<String, Object>> result = new ArrayList<>();
    for (String name : names) {
      String status =
          !previous.containsKey(name)
              ? "ADDED"
              : !current.containsKey(name)
                  ? "REMOVED"
                  : previous.get(name).equals(current.get(name)) ? "UNCHANGED" : "CHANGED";
      result.add(
          Map.of(
              "name",
              name,
              "status",
              status,
              "currentDigest",
              current.getOrDefault(name, ""),
              "previousDigest",
              previous.getOrDefault(name, "")));
    }
    return result;
  }

  public void requestApproval(
      long discoveredToolId,
      String toolKey,
      String version,
      String riskLevel,
      List<String> permissions,
      long adminId) {
    validateTarget(toolKey, version, riskLevel, permissions);
    int inserted =
        jdbc.sql(
                """
INSERT INTO ai_runtime_mcp_approval(server_id,discovery_snapshot_id,discovered_tool_id,
  risk_level,required_permissions,target_tool_key,target_version,requested_by)
SELECT d.server_id,t.discovery_snapshot_id,t.id,:risk,CAST(:permissions AS JSON),
  :toolKey,:version,:admin FROM ai_runtime_mcp_discovered_tool t
JOIN ai_runtime_mcp_discovery_snapshot d ON d.id=t.discovery_snapshot_id
WHERE t.id=:tool AND d.status='CURRENT'
""")
            .param("risk", riskLevel)
            .param("permissions", write(permissions))
            .param("toolKey", toolKey)
            .param("version", version)
            .param("admin", adminId)
            .param("tool", discoveredToolId)
            .update();
    if (inserted != 1) throw new IllegalArgumentException("MCP discovered tool is not current");
  }

  public void decide(long approvalId, boolean approve, String note, long adminId) {
    transactions.executeWithoutResult(
        status -> {
          Approval row =
              jdbc.sql(
                      """
SELECT a.id,a.server_id,a.target_tool_key,a.target_version,a.risk_level,
  a.required_permissions,a.decision,d.remote_tool_name,d.description,d.input_schema,
  d.output_schema,d.schema_digest
FROM ai_runtime_mcp_approval a JOIN ai_runtime_mcp_discovered_tool d ON d.id=a.discovered_tool_id
WHERE a.id=:id FOR UPDATE
""")
                  .param("id", approvalId)
                  .query(Approval.class)
                  .optional()
                  .orElseThrow(() -> new IllegalArgumentException("MCP approval does not exist"));
          if (!"PENDING".equals(row.decision()))
            throw new IllegalStateException("MCP approval already reviewed");
          if (approve) publish(row, adminId);
          jdbc.sql(
                  """
                  UPDATE ai_runtime_mcp_approval SET decision=:decision,reviewed_by=:admin,
                    review_note=:note,reviewed_at=CURRENT_TIMESTAMP(6) WHERE id=:id
                  """)
              .param("decision", approve ? "APPROVED" : "REJECTED")
              .param("admin", adminId)
              .param("note", limited(note, 1000))
              .param("id", approvalId)
              .update();
        });
  }

  private void publish(Approval row, long adminId) {
    Long toolId =
        jdbc.sql("SELECT id FROM ai_runtime_tool WHERE tool_key=:key")
            .param("key", row.targetToolKey())
            .query(Long.class)
            .optional()
            .orElse(null);
    if (toolId == null) {
      jdbc.sql(
              """
INSERT INTO ai_runtime_tool(tool_key,name,description,status,risk_level,created_by,updated_by)
VALUES (:key,:name,:description,'ACTIVE',:risk,:admin,:admin)
""")
          .param("key", row.targetToolKey())
          .param("name", row.targetToolKey())
          .param("description", limited(row.description(), 1000))
          .param("risk", row.riskLevel())
          .param("admin", adminId)
          .update();
      toolId = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    }
    String output = row.outputSchema() == null ? "{\"type\":\"object\"}" : row.outputSchema();
    String permissions = "{\"requiredPermissions\":" + row.requiredPermissions() + "}";
    String checksum =
        digest(row.inputSchema() + "\n" + output + "\n" + permissions + "\n" + row.schemaDigest());
    jdbc.sql(
            """
INSERT INTO ai_runtime_tool_version(tool_id,version,lifecycle_status,transport_type,executor_key,
  input_schema,output_schema,permission_policy,configuration,timeout_ms,max_result_bytes,
  checksum,created_by,published_by,published_at)
VALUES (:toolId,:version,'PUBLISHED','MCP','mcp-gateway',CAST(:input AS JSON),
  CAST(:output AS JSON),CAST(:permissions AS JSON),JSON_OBJECT('managedBy','mcp-approval'),
  30000,1048576,:checksum,:admin,:admin,CURRENT_TIMESTAMP(6))
""")
        .param("toolId", toolId)
        .param("version", row.targetVersion())
        .param("input", row.inputSchema())
        .param("output", output)
        .param("permissions", permissions)
        .param("checksum", checksum)
        .param("admin", adminId)
        .update();
    long versionId = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    jdbc.sql(
            "UPDATE ai_runtime_tool SET current_version_id=:version,updated_by=:admin WHERE"
                + " id=:tool")
        .param("version", versionId)
        .param("admin", adminId)
        .param("tool", toolId)
        .update();
    jdbc.sql(
            """
            INSERT INTO ai_runtime_mcp_tool_binding(tool_version_id,server_id,remote_tool_name,
              pinned_schema_digest,status) VALUES (:version,:server,:remote,:digest,'ACTIVE')
            """)
        .param("version", versionId)
        .param("server", row.serverId())
        .param("remote", row.remoteToolName())
        .param("digest", row.schemaDigest())
        .update();
  }

  private Map<String, String> toolDigests(long snapshotId) {
    Map<String, String> result = new LinkedHashMap<>();
    jdbc.sql(
            "SELECT remote_tool_name,schema_digest FROM ai_runtime_mcp_discovered_tool WHERE"
                + " discovery_snapshot_id=:id")
        .param("id", snapshotId)
        .query()
        .listOfRows()
        .forEach(
            row ->
                result.put(
                    String.valueOf(row.get("remote_tool_name")),
                    String.valueOf(row.get("schema_digest"))));
    return result;
  }

  private void validateTarget(String key, String version, String risk, List<String> permissions) {
    if (key == null
        || !key.matches("[A-Za-z][A-Za-z0-9]*(?:[._-][A-Za-z0-9]+)*")
        || version == null
        || !version.matches("[0-9]+[.][0-9]+[.][0-9]+(?:[+-][0-9A-Za-z.-]+)?")
        || !Set.of("LOW", "MEDIUM", "HIGH").contains(risk)
        || permissions == null
        || permissions.stream().anyMatch(value -> !value.matches("[A-Z][A-Z0-9_]{1,79}")))
      throw new IllegalArgumentException("MCP approval target is invalid");
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String limited(String value, int max) {
    if (value != null && value.length() > max)
      throw new IllegalArgumentException("MCP admin text too long");
    return value;
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void validateHttps(String value) {
    try {
      var uri = java.net.URI.create(value);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getFragment() != null) throw new Exception();
    } catch (Exception e) {
      throw new IllegalArgumentException("MCP HTTPS endpoint is invalid");
    }
  }

  public record ServerInput(
      String serverKey,
      String name,
      String description,
      String transportType,
      String endpointUri,
      String authMode,
      String credentialReference,
      String authorizationEndpoint,
      String tokenEndpoint,
      String clientId,
      String scopes,
      List<String> stdioCommand,
      String stdioWorkingDirectory,
      List<String> allowedHosts,
      boolean enabled) {}

  private record Approval(
      long id,
      long serverId,
      String targetToolKey,
      String targetVersion,
      String riskLevel,
      String requiredPermissions,
      String decision,
      String remoteToolName,
      String description,
      String inputSchema,
      String outputSchema,
      String schemaDigest) {}
}
