package cn.finalscompass.ai.runtime.tool;

import java.util.Set;

/**
 * 运行时工具执行上下文的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：运行时工具定义、权限和执行契约变化时修改这里。
 */
public record RuntimeToolExecutionContext(
    long executionId,
    long nodeId,
    long userId,
    String skillKey,
    Set<String> allowedTools,
    Set<String> grantedPermissions,
    String knowledgeScope) {
  public RuntimeToolExecutionContext {
    if (executionId <= 0 || nodeId <= 0 || userId <= 0 || skillKey == null || skillKey.isBlank())
      throw new IllegalArgumentException("Runtime Tool execution context is invalid");
    allowedTools = Set.copyOf(allowedTools);
    grantedPermissions = Set.copyOf(grantedPermissions);
  }

  public RuntimeToolExecutionContext(
      long executionId,
      long nodeId,
      long userId,
      String skillKey,
      Set<String> allowedTools,
      Set<String> grantedPermissions) {
    this(executionId, nodeId, userId, skillKey, allowedTools, grantedPermissions, null);
  }
}
