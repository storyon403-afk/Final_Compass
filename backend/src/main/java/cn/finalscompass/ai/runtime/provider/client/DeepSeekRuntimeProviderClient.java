package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 把统一模型命令适配为 DeepSeek Chat Completions 协议，并解析工具调用
 * 维护入口：DeepSeek 协议或能力限制改这里；不要在此实现跨供应商策略
 */
@Component
public final class DeepSeekRuntimeProviderClient implements RuntimeProviderProtocolClient {
  private static final String ADAPTER_KEY = "deepseek-chat-v1";
  private final ObjectMapper json;
  private final RuntimeHttpTransport transport;

  public DeepSeekRuntimeProviderClient(ObjectMapper json, RuntimeHttpTransport transport) {
    this.json = json;
    this.transport = transport;
  }

  @Override
  public String adapterKey() {
    return ADAPTER_KEY;
  }

  /**
   * 调用外部服务并解析返回结果
   * 实现上，先组装协议请求，再通过传输层发送并校验响应；通过 Jackson 完成 JSON 的解析或序列化
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
    validate(command, credential, binaryInput);
    try {
      String userInput = command.userInput() + "\n\n[Runtime Context]\n" + command.contextJson();
      String body =
          json.writeValueAsString(
              Map.of(
                  "model",
                  command.modelKey(),
                  "stream",
                  false,
                  "temperature",
                  0.2,
                  "messages",
                  List.of(
                      Map.of("role", "system", "content", command.systemInstruction()),
                      Map.of("role", "user", "content", userInput))));
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
                  4 * 1024 * 1024));
      if (response.statusCode() / 100 != 2)
        throw failure(
            "DEEPSEEK_HTTP_" + response.statusCode(),
            response.statusCode(),
            transientStatus(response.statusCode()),
            null);
      Map<String, Object> parsed = json.readValue(response.body(), new TypeReference<>() {});
      String content = content(parsed);
      Map<?, ?> usage = parsed.get("usage") instanceof Map<?, ?> value ? value : Map.of();
      return new RuntimeProviderClientResult(
          content,
          number(usage.get("prompt_tokens")),
          number(usage.get("completion_tokens")),
          false,
          requestId(response.headers()),
          List.of(),
          null);
    } catch (RuntimeProviderClientException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure("DEEPSEEK_REQUEST_FAILED", null, timeout(exception), exception);
    }
  }

  /**
   * 校验定义及其关联配置
   *
   * @param command 已经归一化的执行命令
   * @param credential 本次调用使用的凭据
   * @param binaryInput 可选的图片等二进制输入
   */
  private void validate(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput) {
    if (command == null
        || credential == null
        || !ADAPTER_KEY.equals(command.adapterKey())
        || !"deepseek".equals(command.providerKey())
        || command.connectTimeoutMs() < 100
        || command.timeoutMs() < command.connectTimeoutMs())
      throw new IllegalArgumentException("DeepSeek Runtime command is invalid");
    if (!command.providerKey().equals(credential.provider())
        || !command.modelKey().equals(credential.model())
        || !command.credentialSource().equals(credential.source().name()))
      throw new SecurityException("DeepSeek credential does not match Runtime command");
    if (binaryInput != null || command.modalities().contains("IMAGE"))
      throw new IllegalArgumentException("DeepSeek Runtime client accepts text only");
    if (!command.allowedTools().isEmpty())
      throw new IllegalArgumentException("DeepSeek native Tool schema is not configured");
  }

  // 规范化并校验供应商接口地址
  private URI endpoint(String value) {
    URI base = URI.create(value);
    if (!"https".equalsIgnoreCase(base.getScheme())
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null)
      throw new IllegalArgumentException("DeepSeek endpoint is invalid");
    String normalized = base.toString().replaceAll("/+$", "");
    return URI.create(normalized + "/chat/completions");
  }

  // 从供应商响应中提取最终文本
  private String content(Map<String, Object> parsed) {
    if (!(parsed.get("choices") instanceof List<?> choices)
        || choices.isEmpty()
        || !(choices.getFirst() instanceof Map<?, ?> choice)
        || !(choice.get("message") instanceof Map<?, ?> message)
        || !(message.get("content") instanceof String value)
        || value.isBlank()) throw failure("DEEPSEEK_EMPTY", null, false, null);
    return value;
  }

  private int number(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  // 从响应头提取链路追踪 ID。利用流式过滤和排序得到符合约束的稳定结果
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

  // 判断异常链中是否包含超时异常
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
