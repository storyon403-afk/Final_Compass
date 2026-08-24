package cn.finalscompass.ai.runtime.trace;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 集中声明执行、节点和供应商调用允许的状态转换
 * 维护入口：增加状态或调整生命周期时先改这里，再检查 TraceStore 和前端状态展示
 */
@Component
public final class RuntimeTraceStateMachine {
  private static final Map<RuntimeExecutionStatus, Set<RuntimeExecutionStatus>> EXECUTION =
      Map.of(
          RuntimeExecutionStatus.CREATED,
              Set.of(
                  RuntimeExecutionStatus.PLANNING,
                  RuntimeExecutionStatus.RUNNING,
                  RuntimeExecutionStatus.FAILED,
                  RuntimeExecutionStatus.CANCELLED),
          RuntimeExecutionStatus.PLANNING,
              Set.of(
                  RuntimeExecutionStatus.RUNNING,
                  RuntimeExecutionStatus.WAITING_USER,
                  RuntimeExecutionStatus.WAITING_TOOL,
                  RuntimeExecutionStatus.FAILED,
                  RuntimeExecutionStatus.CANCELLED),
          RuntimeExecutionStatus.RUNNING,
              Set.of(
                  RuntimeExecutionStatus.WAITING_USER,
                  RuntimeExecutionStatus.WAITING_TOOL,
                  RuntimeExecutionStatus.RETRYING,
                  RuntimeExecutionStatus.SUCCEEDED,
                  RuntimeExecutionStatus.FAILED,
                  RuntimeExecutionStatus.CANCELLED),
          RuntimeExecutionStatus.WAITING_USER,
              Set.of(
                  RuntimeExecutionStatus.RUNNING,
                  RuntimeExecutionStatus.FAILED,
                  RuntimeExecutionStatus.CANCELLED),
          RuntimeExecutionStatus.WAITING_TOOL,
              Set.of(
                  RuntimeExecutionStatus.RUNNING,
                  RuntimeExecutionStatus.FAILED,
                  RuntimeExecutionStatus.CANCELLED),
          RuntimeExecutionStatus.RETRYING,
              Set.of(
                  RuntimeExecutionStatus.RUNNING,
                  RuntimeExecutionStatus.FAILED,
                  RuntimeExecutionStatus.CANCELLED));
  private static final Map<RuntimeExecutionNodeStatus, Set<RuntimeExecutionNodeStatus>> NODE =
      Map.of(
          RuntimeExecutionNodeStatus.PENDING,
              Set.of(
                  RuntimeExecutionNodeStatus.READY,
                  RuntimeExecutionNodeStatus.SKIPPED,
                  RuntimeExecutionNodeStatus.CANCELLED),
          RuntimeExecutionNodeStatus.READY,
              Set.of(
                  RuntimeExecutionNodeStatus.RUNNING,
                  RuntimeExecutionNodeStatus.SKIPPED,
                  RuntimeExecutionNodeStatus.CANCELLED),
          RuntimeExecutionNodeStatus.RUNNING,
              Set.of(
                  RuntimeExecutionNodeStatus.WAITING_USER,
                  RuntimeExecutionNodeStatus.WAITING_TOOL,
                  RuntimeExecutionNodeStatus.RETRYING,
                  RuntimeExecutionNodeStatus.SUCCEEDED,
                  RuntimeExecutionNodeStatus.FAILED,
                  RuntimeExecutionNodeStatus.CANCELLED),
          RuntimeExecutionNodeStatus.WAITING_USER,
              Set.of(
                  RuntimeExecutionNodeStatus.RUNNING,
                  RuntimeExecutionNodeStatus.FAILED,
                  RuntimeExecutionNodeStatus.CANCELLED),
          RuntimeExecutionNodeStatus.WAITING_TOOL,
              Set.of(
                  RuntimeExecutionNodeStatus.RUNNING,
                  RuntimeExecutionNodeStatus.FAILED,
                  RuntimeExecutionNodeStatus.CANCELLED),
          RuntimeExecutionNodeStatus.RETRYING,
              Set.of(
                  RuntimeExecutionNodeStatus.RUNNING,
                  RuntimeExecutionNodeStatus.FAILED,
                  RuntimeExecutionNodeStatus.CANCELLED));
  private static final Map<RuntimeProviderInvocationStatus, Set<RuntimeProviderInvocationStatus>>
      INVOCATION =
          Map.of(
              RuntimeProviderInvocationStatus.ACCEPTED,
                  Set.of(
                      RuntimeProviderInvocationStatus.RUNNING,
                      RuntimeProviderInvocationStatus.SUCCEEDED,
                      RuntimeProviderInvocationStatus.FAILED,
                      RuntimeProviderInvocationStatus.TIMEOUT,
                      RuntimeProviderInvocationStatus.CANCELLED),
              RuntimeProviderInvocationStatus.RUNNING,
                  Set.of(
                      RuntimeProviderInvocationStatus.SUCCEEDED,
                      RuntimeProviderInvocationStatus.FAILED,
                      RuntimeProviderInvocationStatus.TIMEOUT,
                      RuntimeProviderInvocationStatus.CANCELLED));

  // 校验状态是否允许从当前值流转到目标值。状态变化先经过状态机约束，阻止非法跳转
  public void requireTransition(RuntimeExecutionStatus from, RuntimeExecutionStatus to) {
    if (!EXECUTION.getOrDefault(from, Set.of()).contains(to))
      throw new IllegalStateException("Invalid execution status transition: " + from + " -> " + to);
  }

  // 校验状态是否允许从当前值流转到目标值。状态变化先经过状态机约束，阻止非法跳转
  public void requireTransition(RuntimeExecutionNodeStatus from, RuntimeExecutionNodeStatus to) {
    if (!NODE.getOrDefault(from, Set.of()).contains(to))
      throw new IllegalStateException(
          "Invalid execution node status transition: " + from + " -> " + to);
  }

  // 校验状态是否允许从当前值流转到目标值。状态变化先经过状态机约束，阻止非法跳转
  public void requireTransition(
      RuntimeProviderInvocationStatus from, RuntimeProviderInvocationStatus to) {
    if (!INVOCATION.getOrDefault(from, Set.of()).contains(to))
      throw new IllegalStateException(
          "Invalid provider invocation status transition: " + from + " -> " + to);
  }
}
