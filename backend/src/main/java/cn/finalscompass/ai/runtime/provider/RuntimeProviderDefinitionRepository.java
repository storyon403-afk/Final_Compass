package cn.finalscompass.ai.runtime.provider;

import java.util.List;
import java.util.Optional;

public interface RuntimeProviderDefinitionRepository {
  List<RuntimeProviderDefinition> findRoutable();

  Optional<RuntimeProviderDefinition> findRoutableByKey(String providerKey);
}
