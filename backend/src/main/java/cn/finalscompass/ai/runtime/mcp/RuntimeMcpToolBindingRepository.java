package cn.finalscompass.ai.runtime.mcp;

import java.util.Optional;

public interface RuntimeMcpToolBindingRepository {
  Optional<RuntimeMcpToolBinding> findActive(String toolKey, String toolVersion);
}
