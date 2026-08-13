package cn.finalscompass.ai.runtime.tool;

/**
 * 运行时工具调用结果的数据载体，用于在相邻运行时组件之间传递不可变数据。
 * 维护入口：运行时工具定义、权限和执行契约变化时修改这里。
 */
public record RuntimeToolCallResult(
    String callId, String toolKey, boolean success, String outputJson, String errorCode) {}
