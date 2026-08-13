package cn.finalscompass.ai.runtime.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 从数据库加载完整供应商定义，并组装模型、端点和凭据来源。
 * 维护入口：表结构或定义聚合方式变化时改这里；业务合法性由 Validator 负责。
 */
@Repository
public class JdbcRuntimeProviderDefinitionRepository
    implements RuntimeProviderDefinitionRepository {
  private static final String SELECT_PROVIDER =
      """
      SELECT id,provider_key,name,provider_type,adapter_key,status,credential_policy,configuration
      FROM ai_runtime_provider WHERE status IN ('ACTIVE','DEGRADED')
      """;
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final RuntimeProviderDefinitionValidator validator;

  public JdbcRuntimeProviderDefinitionRepository(
      JdbcClient jdbc, ObjectMapper json, RuntimeProviderDefinitionValidator validator) {
    this.jdbc = jdbc;
    this.json = json;
    this.validator = validator;
  }

  // 查询业务数据。
  @Override
  public List<RuntimeProviderDefinition> findRoutable() {
    return jdbc
        .sql(SELECT_PROVIDER + " ORDER BY provider_key")
        .query(ProviderRow.class)
        .list()
        .stream()
        .map(this::assemble)
        .toList();
  }

  // 查询业务数据。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
  @Override
  public Optional<RuntimeProviderDefinition> findRoutableByKey(String providerKey) {
    if (providerKey == null || providerKey.isBlank()) return Optional.empty();
    return jdbc.sql(SELECT_PROVIDER + " AND provider_key=:providerKey")
        .param("providerKey", providerKey)
        .query(ProviderRow.class)
        .optional()
        .map(this::assemble);
  }

  // 组合供应商、模型和端点记录形成完整定义。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象。
  // 可升级：该方法职责较多，后续可按校验、执行和结果持久化拆分。
  private RuntimeProviderDefinition assemble(ProviderRow row) {
    try {
      List<RuntimeProviderEndpoint> endpoints =
          jdbc.sql(
                  """
                  SELECT id,endpoint_key,base_url,region,priority,weight,status,connect_timeout_ms,
                    request_timeout_ms,health_check_path,configuration
                  FROM ai_runtime_provider_endpoint
                  WHERE provider_id=:providerId AND status IN ('ACTIVE','DEGRADED')
                  ORDER BY priority,status,weight DESC,endpoint_key
                  """)
              .param("providerId", row.id())
              .query(
                  (result, number) ->
                      new RuntimeProviderEndpoint(
                          result.getLong("id"),
                          result.getString("endpoint_key"),
                          result.getString("base_url"),
                          result.getString("region"),
                          result.getInt("priority"),
                          result.getInt("weight"),
                          enumValue(RuntimeProviderStatus.class, result.getString("status")),
                          result.getInt("connect_timeout_ms"),
                          result.getInt("request_timeout_ms"),
                          result.getString("health_check_path"),
                          result.getString("configuration")))
              .list();
      List<RuntimeProviderModel> models =
          jdbc.sql(
                  """
SELECT id,model_key,display_name,status,context_window,max_output_units,
  supports_structured_output,supports_tool_calling,input_unit_price,output_unit_price,
  currency,routing_priority,routing_weight,configuration
FROM ai_runtime_provider_model
WHERE provider_id=:providerId AND status='ACTIVE'
ORDER BY routing_priority,routing_weight DESC,model_key
""")
              .param("providerId", row.id())
              .query(
                  (result, number) -> {
                    long modelId = result.getLong("id");
                    Set<String> capabilities =
                        new LinkedHashSet<>(
                            jdbc.sql(
                                    """
SELECT c.capability_key FROM ai_runtime_provider_model_capability mc
JOIN ai_runtime_capability c ON c.id=mc.capability_id
WHERE mc.provider_model_id=:modelId AND c.status='ACTIVE'
ORDER BY c.capability_key
""")
                                .param("modelId", modelId)
                                .query(String.class)
                                .list());
                    return new RuntimeProviderModel(
                        modelId,
                        result.getString("model_key"),
                        result.getString("display_name"),
                        enumValue(RuntimeProviderModelStatus.class, result.getString("status")),
                        nullableInteger(result, "context_window"),
                        nullableInteger(result, "max_output_units"),
                        result.getBoolean("supports_structured_output"),
                        result.getBoolean("supports_tool_calling"),
                        result.getBigDecimal("input_unit_price"),
                        result.getBigDecimal("output_unit_price"),
                        result.getString("currency"),
                        result.getInt("routing_priority"),
                        result.getInt("routing_weight"),
                        result.getString("configuration"),
                        capabilities);
                  })
              .list();
      RuntimeProviderDefinition provider =
          new RuntimeProviderDefinition(
              row.id(),
              row.providerKey(),
              row.name(),
              enumValue(RuntimeProviderType.class, row.providerType()),
              row.adapterKey(),
              enumValue(RuntimeProviderStatus.class, row.status()),
              credentialSources(row.credentialPolicy()),
              row.credentialPolicy(),
              row.configuration(),
              endpoints,
              models);
      validator.validate(provider);
      return provider;
    } catch (InvalidRuntimeProviderDefinitionException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new InvalidRuntimeProviderDefinitionException(
          row.providerKey(), "definition", "INVALID_DATABASE_VALUE");
    }
  }

  // 解析供应商允许使用的凭据来源。通过 Jackson 完成 JSON 的解析或序列化。
  private Set<String> credentialSources(String value) throws Exception {
    JsonNode sources = json.readTree(value).path("supportedSources");
    if (!sources.isArray()) throw new IllegalArgumentException("missing supportedSources");
    var values = new LinkedHashSet<String>();
    for (JsonNode source : sources)
      if (!source.isTextual() || !values.add(source.textValue()))
        throw new IllegalArgumentException("invalid supportedSources");
    return Set.copyOf(values);
  }

  private <T extends Enum<T>> T enumValue(Class<T> type, String value) {
    return Enum.valueOf(type, value);
  }

  // 读取允许为空的整数字段。
  private Integer nullableInteger(java.sql.ResultSet result, String column)
      throws java.sql.SQLException {
    int value = result.getInt(column);
    return result.wasNull() ? null : value;
  }

  private record ProviderRow(
      long id,
      String providerKey,
      String name,
      String providerType,
      String adapterKey,
      String status,
      String credentialPolicy,
      String configuration) {}
}
