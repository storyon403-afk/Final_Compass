package cn.finalscompass.ai.runtime.provider;

import java.util.List;
import java.util.Optional;

/**
 * 运行时供应商定义仓储的抽象契约，用于隔离业务编排与具体实现
 * 维护入口：供应商、模型、端点定义及匹配规则变化时修改这里
 */
public interface RuntimeProviderDefinitionRepository {
  List<RuntimeProviderDefinition> findRoutable();

  Optional<RuntimeProviderDefinition> findRoutableByKey(String providerKey);
}
