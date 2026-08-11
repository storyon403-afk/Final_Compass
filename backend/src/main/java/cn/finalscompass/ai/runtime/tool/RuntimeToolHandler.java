package cn.finalscompass.ai.runtime.tool;

public interface RuntimeToolHandler {
    String executorKey();
    String invoke(RuntimeToolDefinition definition, RuntimeToolExecutionContext context, String argumentsJson);
}
