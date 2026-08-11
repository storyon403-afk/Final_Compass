package cn.finalscompass.ai.runtime.tool;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RuntimeToolDefinitionRepository {
    Optional<RuntimeToolDefinition> findActiveByKey(String toolKey);
    List<RuntimeToolDefinition> findActiveByKeys(Collection<String> toolKeys);
}
