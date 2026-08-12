package cn.finalscompass.ai.runtime.mcp;

import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class RuntimeMcpTransportRegistry {
  private final EnumMap<RuntimeMcpTransportType, RuntimeMcpTransport> transports =
      new EnumMap<>(RuntimeMcpTransportType.class);

  public RuntimeMcpTransportRegistry(List<RuntimeMcpTransport> values) {
    for (RuntimeMcpTransport value : values)
      if (transports.putIfAbsent(value.transportType(), value) != null)
        throw new IllegalStateException(
            "Duplicate Runtime MCP transport: " + value.transportType());
  }

  public RuntimeMcpTransport require(RuntimeMcpTransportType type) {
    RuntimeMcpTransport transport = transports.get(type);
    if (transport == null)
      throw new IllegalStateException("Runtime MCP transport is unavailable: " + type);
    return transport;
  }
}
