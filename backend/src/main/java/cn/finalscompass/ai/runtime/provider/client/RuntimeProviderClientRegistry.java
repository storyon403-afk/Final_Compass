package cn.finalscompass.ai.runtime.provider.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 集中注册和查找运行时供应商客户端实现，避免调用方直接依赖具体类。
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里。
 */
@Component
public final class RuntimeProviderClientRegistry {
  private final Map<String, RuntimeProviderProtocolClient> clients;

  public RuntimeProviderClientRegistry(List<RuntimeProviderProtocolClient> values) {
    this.clients =
        values.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    RuntimeProviderProtocolClient::adapterKey,
                    Function.identity(),
                    (left, right) -> {
                      throw new IllegalStateException(
                          "Duplicate Runtime Provider adapter: " + left.adapterKey());
                    }));
  }

  // 按类型查找必需的组件。
  public RuntimeProviderProtocolClient require(String adapterKey) {
    RuntimeProviderProtocolClient client = clients.get(adapterKey);
    if (client == null)
      throw new IllegalStateException("Runtime Provider adapter is not registered: " + adapterKey);
    return client;
  }

  public Optional<RuntimeProviderProtocolClient> find(String adapterKey) {
    return Optional.ofNullable(clients.get(adapterKey));
  }
}
