package cn.finalscompass.ai.runtime.mcp;

import java.util.Optional;

public interface RuntimeMcpServerRepository {
    Optional<RuntimeMcpServerDefinition> findActiveByKey(String serverKey);
}
