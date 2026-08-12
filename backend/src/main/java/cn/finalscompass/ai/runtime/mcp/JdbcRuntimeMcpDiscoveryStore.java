package cn.finalscompass.ai.runtime.mcp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcRuntimeMcpDiscoveryStore implements RuntimeMcpDiscoveryStore {
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  public JdbcRuntimeMcpDiscoveryStore(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public RuntimeMcpDiscoveryPersistResult saveCurrent(RuntimeMcpDiscoverySnapshot snapshot) {
    RuntimeMcpDiscoveryPersistResult result = transactions.execute(status -> save(snapshot));
    if (result == null)
      throw new IllegalStateException("MCP discovery transaction returned no result");
    return result;
  }

  private RuntimeMcpDiscoveryPersistResult save(RuntimeMcpDiscoverySnapshot snapshot) {
    jdbc.sql(
            """
            UPDATE ai_runtime_mcp_discovery_snapshot SET status='SUPERSEDED'
            WHERE server_id=:serverId AND status='CURRENT'
            """)
        .param("serverId", snapshot.serverId())
        .update();
    jdbc.sql(
            """
INSERT INTO ai_runtime_mcp_discovery_snapshot(
  server_id,discovery_id,protocol_version,server_capabilities,tool_count,schema_digest,status)
VALUES (:serverId,:discoveryId,:protocolVersion,CAST(:capabilities AS JSON),
  :toolCount,:schemaDigest,'CURRENT')
""")
        .param("serverId", snapshot.serverId())
        .param("discoveryId", snapshot.discoveryId())
        .param("protocolVersion", snapshot.protocolVersion())
        .param("capabilities", snapshot.capabilitiesJson())
        .param("toolCount", snapshot.tools().size())
        .param("schemaDigest", snapshot.schemaDigest())
        .update();
    long snapshotId = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    for (RuntimeMcpNormalizedTool tool : snapshot.tools())
      jdbc.sql(
              """
INSERT INTO ai_runtime_mcp_discovered_tool(
  discovery_snapshot_id,remote_tool_name,title,description,input_schema,output_schema,
  annotations,schema_digest)
VALUES (:snapshotId,:name,:title,:description,CAST(:inputSchema AS JSON),
  CASE WHEN :outputSchema IS NULL THEN NULL ELSE CAST(:outputSchema AS JSON) END,
  CAST(:annotations AS JSON),:schemaDigest)
""")
          .param("snapshotId", snapshotId)
          .param("name", tool.name())
          .param("title", tool.title())
          .param("description", tool.description())
          .param("inputSchema", tool.inputSchemaJson())
          .param("outputSchema", tool.outputSchemaJson())
          .param("annotations", tool.annotationsJson())
          .param("schemaDigest", tool.schemaDigest())
          .update();
    int stale =
        jdbc.sql(
                """
                UPDATE ai_runtime_mcp_tool_binding b
                SET b.status='STALE'
                WHERE b.server_id=:serverId AND b.status='ACTIVE' AND NOT EXISTS (
                  SELECT 1 FROM ai_runtime_mcp_discovered_tool d
                  WHERE d.discovery_snapshot_id=:snapshotId
                    AND d.remote_tool_name=b.remote_tool_name
                    AND d.schema_digest=b.pinned_schema_digest)
                """)
            .param("serverId", snapshot.serverId())
            .param("snapshotId", snapshotId)
            .update();
    jdbc.sql(
            """
            UPDATE ai_runtime_mcp_server SET last_discovered_at=CURRENT_TIMESTAMP(6),
              last_health_check_at=CURRENT_TIMESTAMP(6),health_status='HEALTHY' WHERE id=:serverId
            """)
        .param("serverId", snapshot.serverId())
        .update();
    return new RuntimeMcpDiscoveryPersistResult(snapshotId, stale);
  }
}
