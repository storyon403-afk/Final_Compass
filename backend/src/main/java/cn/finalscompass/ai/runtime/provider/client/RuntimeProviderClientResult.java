package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.runtime.tool.RuntimeToolCall;
import java.util.List;

/**
 * 运行时供应商客户端结果的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：供应商 HTTP 协议、错误映射或工具调用格式变化时修改这里。
 */
public record RuntimeProviderClientResult(
    String content,
    int inputUnits,
    int outputUnits,
    boolean preview,
    String providerRequestId,
    List<RuntimeToolCall> toolCalls,
    String continuationState) {
  public RuntimeProviderClientResult {
    toolCalls = List.copyOf(toolCalls);
  }
}
