package cn.finalscompass.ai.runtime.tool;

import java.util.Set;

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
