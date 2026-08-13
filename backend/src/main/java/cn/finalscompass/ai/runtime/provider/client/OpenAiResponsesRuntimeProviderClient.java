package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import cn.finalscompass.ai.runtime.tool.RuntimeToolCall;
import cn.finalscompass.ai.runtime.tool.RuntimeToolSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 把统一模型命令适配为 OpenAI Responses 兼容 API，并解析文本、用量和工具调用。
 * 维护入口：Responses 协议字段改这里；通用回退与工具循环不要放这里，应改 ModelClientGateway。
 */
@Component
public final class OpenAiResponsesRuntimeProviderClient implements RuntimeProviderProtocolClient {
  private static final String ADAPTER_KEY = "openai-responses-v1";
  private final ObjectMapper json;
  private final RuntimeHttpTransport transport;

  public OpenAiResponsesRuntimeProviderClient(ObjectMapper json, RuntimeHttpTransport transport) {
    this.json = json;
    this.transport = transport;
  }

  @Override
  public String adapterKey() {
    return ADAPTER_KEY;
  }

  /**
   * 调用外部服务并解析返回结果。
   * 实现上，先组装协议请求，再通过传输层发送并校验响应；通过 Jackson 完成 JSON 的解析或序列化。
   *
   * @param command 已经归一化的执行命令
   * @param credential 本次调用使用的凭据
   * @param binaryInput 可选的图片等二进制输入
   * @return 处理后的业务结果
   */
  @Override
  public RuntimeProviderClientResult invoke(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput) {
    validate(command, credential, binaryInput, false);
    JsonNode outputSchema = command.structuredOutputRequired() ? outputSchema(command) : null;
    try {
      String body = json.writeValueAsString(requestBody(command, binaryInput, outputSchema));
      RuntimeHttpResponse response =
          transport.postJson(
              new RuntimeHttpRequest(
                  endpoint(command.baseUrl()),
                  Duration.ofMillis(command.connectTimeoutMs()),
                  Duration.ofMillis(command.timeoutMs()),
                  Map.of(
                      "Authorization",
                      "Bearer " + new String(credential.apiKey()),
                      "Content-Type",
                      "application/json"),
                  body,
                  8 * 1024 * 1024));
      if (response.statusCode() / 100 != 2)
        throw failure(
            "OPENAI_HTTP_" + response.statusCode(),
            response.statusCode(),
            transientStatus(response.statusCode()),
            null);
      JsonNode parsed = json.readTree(response.body());
      JsonNode usage = parsed.path("usage");
      List<RuntimeToolCall> toolCalls = toolCalls(parsed, command.toolSpecifications());
      return new RuntimeProviderClientResult(
          content(parsed, toolCalls),
          usage.path("input_tokens").asInt(0),
          usage.path("output_tokens").asInt(0),
          false,
          requestId(response.headers()),
          toolCalls,
          toolCalls.isEmpty() ? null : responseId(parsed));
    } catch (RuntimeProviderClientException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure("OPENAI_REQUEST_FAILED", null, timeout(exception), exception);
    }
  }

  /**
   * 把工具执行结果提交给模型并继续上一轮响应。
   * 实现上，先组装协议请求，再通过传输层发送并校验响应；通过 Jackson 完成 JSON 的解析或序列化。
   *
   * @param command 已经归一化的执行命令
   * @param credential 本次调用使用的凭据
   * @param continuation 上一轮模型响应及工具结果
   * @return 处理后的业务结果
   */
  @Override
  public RuntimeProviderClientResult continueWithToolResults(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeProviderContinuation continuation) {
    validate(command, credential, null, true);
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", command.modelKey());
      body.put("previous_response_id", continuation.opaqueState());
      body.put(
          "input",
          continuation.toolResults().stream()
              .map(
                  result ->
                      Map.of(
                          "type",
                          "function_call_output",
                          "call_id",
                          result.callId(),
                          "output",
                          result.outputJson()))
              .toList());
      if (!command.toolSpecifications().isEmpty()) body.put("tools", toolDeclarations(command));
      RuntimeHttpResponse response =
          transport.postJson(
              new RuntimeHttpRequest(
                  endpoint(command.baseUrl()),
                  Duration.ofMillis(command.connectTimeoutMs()),
                  Duration.ofMillis(command.timeoutMs()),
                  Map.of(
                      "Authorization",
                      "Bearer " + new String(credential.apiKey()),
                      "Content-Type",
                      "application/json"),
                  json.writeValueAsString(body),
                  8 * 1024 * 1024));
      if (response.statusCode() / 100 != 2)
        throw failure(
            "OPENAI_HTTP_" + response.statusCode(),
            response.statusCode(),
            transientStatus(response.statusCode()),
            null);
      JsonNode parsed = json.readTree(response.body());
      List<RuntimeToolCall> calls = toolCalls(parsed, command.toolSpecifications());
      JsonNode usage = parsed.path("usage");
      return new RuntimeProviderClientResult(
          content(parsed, calls),
          usage.path("input_tokens").asInt(0),
          usage.path("output_tokens").asInt(0),
          false,
          requestId(response.headers()),
          calls,
          calls.isEmpty() ? null : responseId(parsed));
    } catch (RuntimeProviderClientException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure("OPENAI_REQUEST_FAILED", null, timeout(exception), exception);
    }
  }

  /**
   * 把统一调用命令转换为供应商协议请求体。
   * 实现上，在结束时主动释放资源或擦除敏感数据；通过摘要或 Base64 编码生成稳定且可传输的标识。
   *
   * @param command 已经归一化的执行命令
   * @param binaryInput 可选的图片等二进制输入
   * @param outputSchema 结构化输出约束
   * @return 处理后的业务结果
   */
  private Map<String, Object> requestBody(
      RuntimeModelInvocationCommand command,
      RuntimeBinaryInput binaryInput,
      JsonNode outputSchema) {
    List<Map<String, Object>> content = new ArrayList<>();
    content.add(
        Map.of(
            "type",
            "input_text",
            "text",
            command.userInput() + "\n\n[Runtime Context]\n" + command.contextJson()));
    if (binaryInput != null) {
      byte[] bytes = binaryInput.copyBytes();
      try {
        content.add(
            Map.of(
                "type",
                "input_image",
                "detail",
                "high",
                "image_url",
                "data:"
                    + binaryInput.mediaType()
                    + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes)));
      } finally {
        Arrays.fill(bytes, (byte) 0);
      }
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", command.modelKey());
    body.put("instructions", command.systemInstruction());
    body.put("input", List.of(Map.of("role", "user", "content", content)));
    if (outputSchema != null)
      body.put(
          "text",
          Map.of(
              "format",
              Map.of(
                  "type",
                  "json_schema",
                  "name",
                  schemaName(command.skillKey()),
                  "schema",
                  outputSchema,
                  "strict",
                  true)));
    if (!command.toolSpecifications().isEmpty()) body.put("tools", toolDeclarations(command));
    return body;
  }

  // 把内部工具定义转换为供应商协议格式。
  private List<Map<String, Object>> toolDeclarations(RuntimeModelInvocationCommand command) {
    return command.toolSpecifications().stream()
        .map(
            tool ->
                Map.of(
                    "type",
                    "function",
                    "name",
                    tool.providerName(),
                    "description",
                    tool.description(),
                    "parameters",
                    schema(tool.inputSchemaJson()),
                    "strict",
                    true))
        .toList();
  }

  /**
   * 校验定义及其关联配置。
   *
   * @param command 已经归一化的执行命令
   * @param credential 本次调用使用的凭据
   * @param binaryInput 可选的图片等二进制输入
   * @param continuation 上一轮模型响应及工具结果
   */
  private void validate(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput,
      boolean continuation) {
    if (command == null
        || credential == null
        || !ADAPTER_KEY.equals(command.adapterKey())
        || !"openai".equals(command.providerKey())
        || command.connectTimeoutMs() < 100
        || command.timeoutMs() < command.connectTimeoutMs())
      throw new IllegalArgumentException("OpenAI Runtime command is invalid");
    if (!command.providerKey().equals(credential.provider())
        || !command.modelKey().equals(credential.model())
        || !command.credentialSource().equals(credential.source().name()))
      throw new SecurityException("OpenAI credential does not match Runtime command");
    validateTools(command);
    boolean imageRequired = command.modalities().contains("IMAGE");
    if (!continuation && (binaryInput == null) == imageRequired)
      throw new IllegalArgumentException("OpenAI image modality and binary input do not match");
    if (binaryInput != null && !binaryInput.mediaType().startsWith("image/"))
      throw new IllegalArgumentException("OpenAI Runtime accepts image binary input only");
  }

  // 校验定义及其关联配置。
  private void validateTools(RuntimeModelInvocationCommand command) {
    if (command.allowedTools().size() != command.toolSpecifications().size()
        || !command.toolSpecifications().stream()
            .map(RuntimeToolSpecification::toolKey)
            .collect(java.util.stream.Collectors.toSet())
            .equals(command.allowedTools()))
      throw new IllegalArgumentException(
          "OpenAI Runtime Tool specifications do not match allowlist");
  }

  // 解析工具参数 Schema。通过 Jackson 完成 JSON 的解析或序列化。
  private JsonNode schema(String value) {
    try {
      JsonNode schema = json.readTree(value);
      if (schema == null || !schema.isObject()) throw new IllegalArgumentException();
      return schema;
    } catch (Exception exception) {
      throw new IllegalArgumentException("OpenAI Runtime Tool schema is invalid", exception);
    }
  }

  // 解析并校验结构化输出 Schema。通过 Jackson 完成 JSON 的解析或序列化。
  private JsonNode outputSchema(RuntimeModelInvocationCommand command) {
    try {
      JsonNode schema = json.readTree(command.outputSchemaJson());
      if (schema == null || !schema.isObject() || schema.isEmpty())
        throw new IllegalArgumentException("OpenAI structured output schema is invalid");
      return schema;
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("OpenAI structured output schema is invalid", exception);
    }
  }

  // 规范化并校验供应商接口地址。
  private URI endpoint(String value) {
    URI base = URI.create(value);
    if (!"https".equalsIgnoreCase(base.getScheme())
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null)
      throw new IllegalArgumentException("OpenAI endpoint is invalid");
    return URI.create(base.toString().replaceAll("/+$", "") + "/v1/responses");
  }

  // 从供应商响应中提取最终文本。
  private String content(JsonNode parsed, List<RuntimeToolCall> toolCalls) {
    String direct = parsed.path("output_text").asText("");
    if (!direct.isBlank()) return direct;
    for (JsonNode output : parsed.path("output"))
      for (JsonNode part : output.path("content"))
        if ("output_text".equals(part.path("type").asText())
            && !part.path("text").asText("").isBlank()) return part.path("text").asText();
    if (!toolCalls.isEmpty()) return "";
    throw failure("OPENAI_EMPTY", null, false, null);
  }

  // 从供应商响应中提取并校验工具调用。
  private List<RuntimeToolCall> toolCalls(
      JsonNode parsed, List<RuntimeToolSpecification> specifications) {
    Map<String, String> names =
        specifications.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    RuntimeToolSpecification::providerName, RuntimeToolSpecification::toolKey));
    List<RuntimeToolCall> result = new ArrayList<>();
    for (JsonNode output : parsed.path("output"))
      if ("function_call".equals(output.path("type").asText())) {
        String toolKey = names.get(output.path("name").asText());
        if (toolKey == null) throw failure("OPENAI_UNAUTHORIZED_TOOL_CALL", null, false, null);
        result.add(
            new RuntimeToolCall(
                output.path("call_id").asText(), toolKey, output.path("arguments").asText("{}")));
      }
    return List.copyOf(result);
  }

  // 提取后续调用需要的响应 ID。
  private String responseId(JsonNode parsed) {
    String id = parsed.path("id").asText("");
    if (id.isBlank() || id.length() > 512)
      throw failure("OPENAI_CONTINUATION_STATE_MISSING", null, false, null);
    return id;
  }

  private String schemaName(String skillKey) {
    String value = skillKey.replaceAll("[^A-Za-z0-9_-]", "_");
    return value.substring(0, Math.min(value.length(), 64));
  }

  // 从响应头提取链路追踪 ID。利用流式过滤和排序得到符合约束的稳定结果。
  private String requestId(Map<String, List<String>> headers) {
    return headers.entrySet().stream()
        .filter(entry -> "x-request-id".equalsIgnoreCase(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .findFirst()
        .filter(value -> value.length() <= 160)
        .orElse(null);
  }

  private boolean transientStatus(int status) {
    return status == 429 || status >= 500;
  }

  // 判断异常链中是否包含超时异常。
  private boolean timeout(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause())
      if (current.getClass().getSimpleName().toLowerCase().contains("timeout")) return true;
    return false;
  }

  private RuntimeProviderClientException failure(
      String code, Integer status, boolean retryable, Throwable cause) {
    return new RuntimeProviderClientException(code, status, retryable, cause);
  }
}
