package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.runtime.tool.RuntimeToolCallResult;
import java.util.List;

/**
 * 运行时供应商续传上下文的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里。
 */
public record RuntimeProviderContinuation(
    String opaqueState, List<RuntimeToolCallResult> toolResults) {
  public RuntimeProviderContinuation {
    if (opaqueState == null || opaqueState.isBlank() || opaqueState.length() > 10 * 1024 * 1024)
      throw new IllegalArgumentException("Runtime Provider continuation state is invalid");
    toolResults = List.copyOf(toolResults);
    if (toolResults.isEmpty()) throw new IllegalArgumentException("Runtime Tool results are empty");
  }
}
