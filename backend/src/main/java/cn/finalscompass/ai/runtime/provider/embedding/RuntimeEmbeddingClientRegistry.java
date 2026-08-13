package cn.finalscompass.ai.runtime.provider.embedding;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 集中注册和查找运行时向量客户端实现，避免调用方直接依赖具体类。
 * 维护入口：向量供应商协议或批量限制变化时修改这里。
 */
@Component
public final class RuntimeEmbeddingClientRegistry {
  private final Map<String, RuntimeEmbeddingClient> clients;

  public RuntimeEmbeddingClientRegistry(List<RuntimeEmbeddingClient> values) {
    clients =
        values.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    RuntimeEmbeddingClient::adapterKey, Function.identity()));
  }

  // 按类型查找必需的组件。
  public RuntimeEmbeddingClient require(String key) {
    RuntimeEmbeddingClient value = clients.get(key);
    if (value == null) throw new IllegalStateException("Embedding adapter is unavailable: " + key);
    return value;
  }
}
