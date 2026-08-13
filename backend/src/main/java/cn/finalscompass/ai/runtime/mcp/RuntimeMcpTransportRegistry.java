package cn.finalscompass.ai.runtime.mcp;

import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 集中注册和查找运行时MCP传输实现，避免调用方直接依赖具体类。
 * 维护入口：MCP 协议、发现、凭据或治理规则变化时修改这里。
 */
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

  // 按类型查找必需的组件。
  public RuntimeMcpTransport require(RuntimeMcpTransportType type) {
    RuntimeMcpTransport transport = transports.get(type);
    if (transport == null)
      throw new IllegalStateException("Runtime MCP transport is unavailable: " + type);
    return transport;
  }
}
