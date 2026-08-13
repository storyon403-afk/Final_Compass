package cn.finalscompass.ai.runtime.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 从数据库读取已启用的运行时工具定义和权限契约。
 * 维护入口：工具表结构或查询条件变化时改这里；执行安全校验由 RuntimeToolExecutor 负责。
 */
@Repository
public class JdbcRuntimeToolDefinitionRepository implements RuntimeToolDefinitionRepository {
  private static final String SELECT =
      """
SELECT t.id,t.tool_key,t.name,t.description,v.version,v.transport_type,v.executor_key,
  v.input_schema,v.output_schema,v.permission_policy,v.configuration,v.timeout_ms,v.max_result_bytes
FROM ai_runtime_tool t JOIN ai_runtime_tool_version v ON v.id=t.current_version_id
WHERE t.status='ACTIVE' AND v.lifecycle_status='PUBLISHED'
""";
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public JdbcRuntimeToolDefinitionRepository(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // 查询业务数据。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
  @Override
  public Optional<RuntimeToolDefinition> findActiveByKey(String toolKey) {
    if (toolKey == null || toolKey.isBlank()) return Optional.empty();
    return jdbc.sql(SELECT + " AND t.tool_key=:toolKey")
        .param("toolKey", toolKey)
        .query((result, row) -> map(result))
        .optional();
  }

  // 查询业务数据。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
  @Override
  public List<RuntimeToolDefinition> findActiveByKeys(Collection<String> toolKeys) {
    if (toolKeys == null || toolKeys.isEmpty()) return List.of();
    return jdbc.sql(SELECT + " AND t.tool_key IN (:toolKeys) ORDER BY t.tool_key")
        .param("toolKeys", Set.copyOf(toolKeys))
        .query((result, row) -> map(result))
        .list();
  }

  // 把数据库行映射为领域对象。
  private RuntimeToolDefinition map(java.sql.ResultSet result) throws java.sql.SQLException {
    return new RuntimeToolDefinition(
        result.getLong("id"),
        result.getString("tool_key"),
        result.getString("name"),
        result.getString("description"),
        result.getString("version"),
        RuntimeToolTransportType.valueOf(result.getString("transport_type")),
        result.getString("executor_key"),
        result.getString("input_schema"),
        result.getString("output_schema"),
        permissions(result.getString("permission_policy")),
        result.getString("configuration"),
        result.getInt("timeout_ms"),
        result.getInt("max_result_bytes"));
  }

  // 解析并返回工具权限集合。通过 Jackson 完成 JSON 的解析或序列化。
  private Set<String> permissions(String value) {
    try {
      JsonNode permissions = json.readTree(value).path("requiredPermissions");
      if (!permissions.isArray()) throw new IllegalArgumentException();
      Set<String> result = new LinkedHashSet<>();
      for (JsonNode permission : permissions)
        if (!permission.isTextual()
            || permission.textValue().isBlank()
            || !result.add(permission.textValue())) throw new IllegalArgumentException();
      return Set.copyOf(result);
    } catch (Exception exception) {
      throw new IllegalStateException("Runtime Tool permission policy is invalid", exception);
    }
  }
}
