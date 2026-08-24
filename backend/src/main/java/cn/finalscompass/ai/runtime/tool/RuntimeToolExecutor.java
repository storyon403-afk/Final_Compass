package cn.finalscompass.ai.runtime.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/*
 * 维护流程图：
 *   ToolCall --> 查定义 --> 权限校验 --> 输入 Schema
 *       --> Handler.invoke --> 大小限制 --> 输出 Schema --> ToolCallResult
 */
/**
 * 运行时工具的安全执行边界：校验权限、输入 Schema、处理器和输出大小及 Schema
 * 维护入口：新增安全规则优先放这里；工具业务实现放 RuntimeToolHandler，不要绕过统一校验
 */
@Component
public final class RuntimeToolExecutor {
  private final RuntimeToolDefinitionRepository tools;
  private final Map<String, RuntimeToolHandler> handlers;
  private final ObjectMapper json;

  public RuntimeToolExecutor(
      RuntimeToolDefinitionRepository tools, List<RuntimeToolHandler> handlers, ObjectMapper json) {
    this.tools = tools;
    this.json = json;
    this.handlers =
        handlers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    RuntimeToolHandler::executorKey,
                    Function.identity(),
                    (left, right) -> {
                      throw new IllegalStateException(
                          "Duplicate Runtime Tool handler: " + left.executorKey());
                    }));
  }

  // 执行一次运行时调用
  public RuntimeToolCallResult execute(RuntimeToolExecutionContext context, RuntimeToolCall call) {
    if (!context.allowedTools().contains(call.toolKey()))
      throw new SecurityException("Runtime Tool call is outside the Skill allowlist");
    RuntimeToolDefinition definition =
        tools
            .findActiveByKey(call.toolKey())
            .orElseThrow(() -> new IllegalStateException("Runtime Tool is unavailable"));
    if (!context.grantedPermissions().containsAll(definition.requiredPermissions()))
      throw new SecurityException("Runtime Tool permission is not granted");
    validateObject(call.argumentsJson(), definition.inputSchemaJson(), "arguments");
    RuntimeToolHandler handler = handlers.get(definition.executorKey());
    if (handler == null) throw new IllegalStateException("Runtime Tool handler is unavailable");
    String output = handler.invoke(definition, context, call.argumentsJson());
    if (output == null
        || output.getBytes(StandardCharsets.UTF_8).length > definition.maxResultBytes())
      throw new IllegalStateException("Runtime Tool result exceeds its contract");
    validateObject(output, definition.outputSchemaJson(), "result");
    return new RuntimeToolCallResult(call.callId(), call.toolKey(), true, output, null);
  }

  // 校验定义及其关联配置。通过 Jackson 完成 JSON 的解析或序列化
  private void validateObject(String value, String schemaValue, String label) {
    try {
      JsonNode object = json.readTree(value);
      JsonNode schema = json.readTree(schemaValue);
      if (object == null || !object.isObject() || schema == null || !schema.isObject())
        throw new IllegalArgumentException();
      if (!matchesSchema(object, schema)) throw new IllegalArgumentException();
    } catch (Exception exception) {
      throw new IllegalArgumentException(
          "Runtime Tool " + label + " does not match its schema", exception);
    }
  }

  // 筛选满足请求约束的模型候选项
  private boolean matchesSchema(JsonNode value, JsonNode schema) {
    String type = schema.path("type").asText("");
    boolean typeMatches =
        switch (type) {
          case "object" -> value.isObject();
          case "array" -> value.isArray();
          case "string" -> value.isTextual();
          case "integer" -> value.isIntegralNumber();
          case "number" -> value.isNumber();
          case "boolean" -> value.isBoolean();
          case "null" -> value.isNull();
          default -> type.isBlank();
        };
    if (!typeMatches) return false;
    JsonNode allowed = schema.path("enum");
    if (allowed.isArray()) {
      boolean found = false;
      for (JsonNode candidate : allowed)
        if (candidate.equals(value)) {
          found = true;
          break;
        }
      if (!found) return false;
    }
    if (value.isArray() && schema.path("items").isObject())
      for (JsonNode item : value) if (!matchesSchema(item, schema.path("items"))) return false;
    if (!value.isObject()) return true;
    JsonNode required = schema.path("required");
    if (required.isArray())
      for (JsonNode field : required)
        if (!field.isTextual() || !value.has(field.textValue())) return false;
    JsonNode properties = schema.path("properties");
    if (properties.isObject()) {
      for (var field : value.properties()) {
        if (!properties.has(field.getKey())) {
          if (schema.path("additionalProperties").isBoolean()
              && !schema.path("additionalProperties").booleanValue()) return false;
        } else if (!matchesSchema(field.getValue(), properties.path(field.getKey()))) return false;
      }
    }
    return true;
  }
}
