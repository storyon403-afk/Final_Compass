package cn.finalscompass.ai.runtime.tool;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 运行时工具定义仓储的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：运行时工具定义、权限和执行契约变化时修改这里。
 */
public interface RuntimeToolDefinitionRepository {
  Optional<RuntimeToolDefinition> findActiveByKey(String toolKey);

  List<RuntimeToolDefinition> findActiveByKeys(Collection<String> toolKeys);
}
