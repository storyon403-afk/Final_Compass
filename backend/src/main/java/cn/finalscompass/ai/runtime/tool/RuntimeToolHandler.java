package cn.finalscompass.ai.runtime.tool;

/**
 * 运行时工具处理器的抽象契约，用于隔离业务编排与具体实现。
 * 维护入口：运行时工具定义、权限和执行契约变化时修改这里。
 */
public interface RuntimeToolHandler {
  String executorKey();

  String invoke(
      RuntimeToolDefinition definition, RuntimeToolExecutionContext context, String argumentsJson);
}
