package cn.finalscompass.ai.runtime.tool;

import java.util.Set;

/**
 * 运行时工具定义的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：运行时工具定义、权限和执行契约变化时修改这里。
 */
public record RuntimeToolDefinition(
    long id,
    String toolKey,
    String name,
    String description,
    String version,
    RuntimeToolTransportType transportType,
    String executorKey,
    String inputSchemaJson,
    String outputSchemaJson,
    Set<String> requiredPermissions,
    String configurationJson,
    int timeoutMs,
    int maxResultBytes) {
  public RuntimeToolDefinition {
    requiredPermissions = Set.copyOf(requiredPermissions);
  }
}
