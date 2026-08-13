package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 把统一模型命令适配为 OpenAI Chat Completions 兼容协议。
 * 维护入口：兼容协议的公共请求、响应和错误规则改这里；供应商、模型和端点只在数据库注册。
 */
@Component
public final class OpenAiChatCompatibleRuntimeProviderClient
    implements RuntimeProviderProtocolClient {
  public static final String ADAPTER_KEY = "openai-chat-compatible-v1";
  private final ObjectMapper json;
  private final RuntimeHttpTransport transport;

  public OpenAiChatCompatibleRuntimeProviderClient(
      ObjectMapper json, RuntimeHttpTransport transport) {
    this.json = json;
    this.transport = transport;
  }

  @Override
  public String adapterKey() {
    return ADAPTER_KEY;
  }

  /**
   * 调用外部服务并解析返回结果。
   * 实现上只依赖 adapterKey，不硬编码 Provider；Provider 与可信端点的绑定由运行时注册表负责。
   */
  @Override
  public RuntimeProviderClientResult invoke(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput) {
    validate(command, credential, binaryInput);
    try {
      String userInput = command.userInput() + "\n\n[Runtime Context]\n" + command.contextJson();
      String body = json.writeValueAsString(requestBody(command,userInput,binaryInput));
      RuntimeHttpResponse response =
          transport.postJson(
              new RuntimeHttpRequest(
                  endpoint(command.baseUrl()),
                  Duration.ofMillis(command.connectTimeoutMs()),
                  Duration.ofMillis(command.timeoutMs()),
                  Map.of(
                      "Authorization", "Bearer " + new String(credential.apiKey()),
                      "Content-Type", "application/json"),
                  body,
                  4 * 1024 * 1024));
      if (response.statusCode() / 100 != 2)
        throw failure(
            errorPrefix(command.providerKey()) + "_HTTP_" + response.statusCode(),
            response.statusCode(),
            transientStatus(response.statusCode()),
            null);
      Map<String, Object> parsed = json.readValue(response.body(), new TypeReference<>() {});
      Map<?, ?> usage = parsed.get("usage") instanceof Map<?, ?> value ? value : Map.of();
      return new RuntimeProviderClientResult(
          content(parsed, command.providerKey()),
          number(usage.get("prompt_tokens")),
          number(usage.get("completion_tokens")),
          false,
          requestId(response.headers()),
          List.of(),
          null);
    } catch (RuntimeProviderClientException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure(
          errorPrefix(command.providerKey()) + "_REQUEST_FAILED",
          null,
          timeout(exception),
          exception);
    }
  }

  /** 校验统一命令、凭据和当前公共协议已经实现的能力边界。 */
  private void validate(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput) {
    if (command == null
        || credential == null
        || !ADAPTER_KEY.equals(command.adapterKey())
        || command.providerKey() == null
        || command.providerKey().isBlank()
        || command.connectTimeoutMs() < 100
        || command.timeoutMs() < command.connectTimeoutMs())
      throw new IllegalArgumentException("OpenAI-compatible Runtime command is invalid");
    if (!command.providerKey().equals(credential.provider())
        || !command.modelKey().equals(credential.model())
        || !command.credentialSource().equals(credential.source().name()))
      throw new SecurityException("OpenAI-compatible credential does not match Runtime command");
    boolean imageRequired=command.modalities().contains("IMAGE");
    if((binaryInput==null)==imageRequired)
      throw new IllegalArgumentException("OpenAI-compatible image modality and binary input do not match");
    if(binaryInput!=null&&!binaryInput.mediaType().startsWith("image/"))
      throw new IllegalArgumentException("OpenAI-compatible Runtime accepts image input only");
    if (!command.allowedTools().isEmpty())
      throw new IllegalArgumentException("OpenAI-compatible Tool schema is not configured");
    if (command.structuredOutputRequired())
      throw new IllegalArgumentException("OpenAI-compatible structured output is not configured");
  }

  // 文本供应商继续发送字符串 content；视觉供应商按兼容规范发送 image_url Data URL 与文本数组。
  private Map<String,Object> requestBody(RuntimeModelInvocationCommand command,String userInput,RuntimeBinaryInput binaryInput){
    Object userContent=userInput;
    if(binaryInput!=null){String dataUrl="data:"+binaryInput.mediaType()+";base64,"+Base64.getEncoder().encodeToString(binaryInput.copyBytes());userContent=List.of(Map.of("type","image_url","image_url",Map.of("url",dataUrl)),Map.of("type","text","text",userInput));}
    return Map.of("model",command.modelKey(),"stream",false,"temperature",0.2,"messages",List.of(Map.of("role","system","content",command.systemInstruction()),Map.of("role","user","content",userContent)));
  }

  // 规范化并校验供应商接口地址；注册表只提供 base URL，本客户端统一追加协议路径。
  private URI endpoint(String value) {
    URI base = URI.create(value);
    if (!"https".equalsIgnoreCase(base.getScheme())
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null)
      throw new IllegalArgumentException("OpenAI-compatible endpoint is invalid");
    return URI.create(base.toString().replaceAll("/+$", "") + "/chat/completions");
  }

  // 从公共响应结构中提取最终文本，不把供应商原始响应写入异常。
  private String content(Map<String, Object> parsed, String provider) {
    if (!(parsed.get("choices") instanceof List<?> choices)
        || choices.isEmpty()
        || !(choices.getFirst() instanceof Map<?, ?> choice)
        || !(choice.get("message") instanceof Map<?, ?> message)
        || !(message.get("content") instanceof String value)
        || value.isBlank())
      throw failure(errorPrefix(provider) + "_EMPTY", null, false, null);
    return value;
  }

  private int number(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  // 不同兼容服务使用的请求 ID 响应头并不完全一致。
  private String requestId(Map<String, List<String>> headers) {
    return headers.entrySet().stream()
        .filter(
            entry ->
                "x-request-id".equalsIgnoreCase(entry.getKey())
                    || "request-id".equalsIgnoreCase(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .findFirst()
        .filter(value -> value.length() <= 160)
        .orElse(null);
  }

  private boolean transientStatus(int status) {
    return status == 408 || status == 409 || status == 429 || status >= 500;
  }

  // 判断异常链中是否包含超时异常。
  private boolean timeout(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause())
      if (current.getClass().getSimpleName().toLowerCase().contains("timeout")) return true;
    return false;
  }

  private String errorPrefix(String provider) {
    return provider == null || provider.isBlank()
        ? "COMPATIBLE_PROVIDER"
        : provider.toUpperCase().replace('-', '_');
  }

  private RuntimeProviderClientException failure(
      String code, Integer status, boolean retryable, Throwable cause) {
    return new RuntimeProviderClientException(code, status, retryable, cause);
  }
}
