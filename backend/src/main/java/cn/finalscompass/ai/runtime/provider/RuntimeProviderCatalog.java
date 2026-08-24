package cn.finalscompass.ai.runtime.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 提供前端和业务层可见的供应商目录，并校验供应商是否已注册可用
 * 维护入口：新增运行时类型或目录展示字段时改这里；真实模型配置由 provider repository 管理
 */
@Component
public final class RuntimeProviderCatalog {
  /**
   * Hermes 智能体网关通过设置面板配置，但在模型网络之外调度
   * 模型网络
   */
  private static final String AGENT_RUNTIME_KEY = "hermes";

  private final JdbcClient jdbc;

  public RuntimeProviderCatalog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // 判断目标能力当前是否可用。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象；利用流式过滤和排序得到符合约束的稳定结果
  public List<ProviderInfo> available() {
    List<ProviderInfo> values =
        new ArrayList<>(
            jdbc.sql(
                    """
                    SELECT provider_key,name,provider_type FROM ai_runtime_provider
                    WHERE status='ACTIVE' ORDER BY provider_key
                    """)
                .query(
                    (rs, row) ->
                        new ProviderInfo(
                            rs.getString("provider_key"),
                            rs.getString("name"),
                            Set.of(rs.getString("provider_type"))))
                .list());
    values.add(new ProviderInfo(AGENT_RUNTIME_KEY, "Hermes Agent", Set.of("AGENT")));
    return values.stream().sorted(Comparator.comparing(ProviderInfo::id)).toList();
  }

  // 查询当前可用的模型供应商。利用流式过滤和排序得到符合约束的稳定结果
  public List<ProviderInfo> availableModelProviders() {
    return available().stream().filter(item -> !item.capabilities().contains("AGENT")).toList();
  }

  // 按类型查找必需的组件。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  public String require(String provider) {
    String normalized = normalize(provider);
    if (AGENT_RUNTIME_KEY.equals(normalized)) return normalized;
    boolean registered =
        jdbc.sql("SELECT TRUE FROM ai_runtime_provider WHERE provider_key=:key AND status='ACTIVE'")
            .param("key", normalized)
            .query(Boolean.class)
            .optional()
            .orElse(false);
    if (!registered) throw new IllegalArgumentException("当前版本尚未注册该 AI Provider");
    return normalized;
  }

  // 把供应商数据转换为内部统一格式
  public String normalize(String value) {
    String provider = value == null ? "" : value.trim().toLowerCase();
    if (!provider.matches("[a-z0-9][a-z0-9_-]{1,39}"))
      throw new IllegalArgumentException("Provider 标识不合法");
    return provider;
  }

  public record ProviderInfo(String id, String name, Set<String> capabilities) {}
}
