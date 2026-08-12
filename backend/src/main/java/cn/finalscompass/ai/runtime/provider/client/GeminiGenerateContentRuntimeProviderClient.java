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

@Component
public final class GeminiGenerateContentRuntimeProviderClient
    implements RuntimeProviderProtocolClient {
  private static final String ADAPTER_KEY = "gemini-generate-content-v1";
  private final ObjectMapper json;
  private final RuntimeHttpTransport transport;

  public GeminiGenerateContentRuntimeProviderClient(
      ObjectMapper json, RuntimeHttpTransport transport) {
    this.json = json;
    this.transport = transport;
  }

  @Override
  public String adapterKey() {
    return ADAPTER_KEY;
  }

  @Override
  public RuntimeProviderClientResult invoke(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput) {
    validate(command, credential, binaryInput, false);
    JsonNode schema = command.structuredOutputRequired() ? outputSchema(command) : null;
    try {
      String body = json.writeValueAsString(requestBody(command, binaryInput, schema));
      RuntimeHttpResponse response =
          transport.postJson(
              new RuntimeHttpRequest(
                  endpoint(command.baseUrl(), command.modelKey()),
                  Duration.ofMillis(command.connectTimeoutMs()),
                  Duration.ofMillis(command.timeoutMs()),
                  Map.of(
                      "x-goog-api-key",
                      new String(credential.apiKey()),
                      "Content-Type",
                      "application/json"),
                  body,
                  8 * 1024 * 1024));
      if (response.statusCode() / 100 != 2)
        throw failure(
            "GEMINI_HTTP_" + response.statusCode(),
            response.statusCode(),
            transientStatus(response.statusCode()),
            null);
      JsonNode parsed = json.readTree(response.body());
      JsonNode usage = parsed.path("usageMetadata");
      List<RuntimeToolCall> toolCalls = toolCalls(parsed, command.toolSpecifications());
      return new RuntimeProviderClientResult(
          content(parsed, toolCalls),
          usage.path("promptTokenCount").asInt(0),
          usage.path("candidatesTokenCount").asInt(0),
          false,
          requestId(response.headers()),
          toolCalls,
          toolCalls.isEmpty() ? null : continuationState(json.readTree(body), parsed));
    } catch (RuntimeProviderClientException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure("GEMINI_REQUEST_FAILED", null, timeout(exception), exception);
    }
  }

  @Override
  public RuntimeProviderClientResult continueWithToolResults(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeProviderContinuation continuation) {
    validate(command, credential, null, true);
    try {
      JsonNode state = json.readTree(continuation.opaqueState());
      if (!state.path("contents").isArray())
        throw new IllegalArgumentException("Gemini continuation state is invalid");
      var contents =
          ((com.fasterxml.jackson.databind.node.ArrayNode) state.path("contents")).deepCopy();
      List<Map<String, Object>> responses = new ArrayList<>();
      Map<String, String> providerNames =
          command.toolSpecifications().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      RuntimeToolSpecification::toolKey, RuntimeToolSpecification::providerName));
      for (var result : continuation.toolResults()) {
        String name = providerNames.get(result.toolKey());
        if (name == null) throw failure("GEMINI_UNAUTHORIZED_TOOL_RESULT", null, false, null);
        JsonNode output = json.readTree(result.outputJson());
        responses.add(Map.of("functionResponse", Map.of("name", name, "response", output)));
      }
      contents.add(json.valueToTree(Map.of("role", "user", "parts", responses)));
      Map<String, Object> body = continuationBody(command, contents);
      RuntimeHttpResponse response =
          transport.postJson(
              new RuntimeHttpRequest(
                  endpoint(command.baseUrl(), command.modelKey()),
                  Duration.ofMillis(command.connectTimeoutMs()),
                  Duration.ofMillis(command.timeoutMs()),
                  Map.of(
                      "x-goog-api-key",
                      new String(credential.apiKey()),
                      "Content-Type",
                      "application/json"),
                  json.writeValueAsString(body),
                  8 * 1024 * 1024));
      if (response.statusCode() / 100 != 2)
        throw failure(
            "GEMINI_HTTP_" + response.statusCode(),
            response.statusCode(),
            transientStatus(response.statusCode()),
            null);
      JsonNode parsed = json.readTree(response.body());
      List<RuntimeToolCall> calls = toolCalls(parsed, command.toolSpecifications());
      JsonNode usage = parsed.path("usageMetadata");
      JsonNode request = json.valueToTree(body);
      return new RuntimeProviderClientResult(
          content(parsed, calls),
          usage.path("promptTokenCount").asInt(0),
          usage.path("candidatesTokenCount").asInt(0),
          false,
          requestId(response.headers()),
          calls,
          calls.isEmpty() ? null : continuationState(request, parsed));
    } catch (RuntimeProviderClientException exception) {
      throw exception;
    } catch (Exception exception) {
      throw failure("GEMINI_REQUEST_FAILED", null, timeout(exception), exception);
    }
  }

  private Map<String, Object> requestBody(
      RuntimeModelInvocationCommand command, RuntimeBinaryInput binaryInput, JsonNode schema) {
    List<Map<String, Object>> parts = new ArrayList<>();
    parts.add(
        Map.of("text", command.userInput() + "\n\n[Runtime Context]\n" + command.contextJson()));
    if (binaryInput != null) {
      byte[] bytes = binaryInput.copyBytes();
      try {
        parts.add(
            Map.of(
                "inlineData",
                Map.of(
                    "mimeType",
                    binaryInput.mediaType(),
                    "data",
                    Base64.getEncoder().encodeToString(bytes))));
      } finally {
        Arrays.fill(bytes, (byte) 0);
      }
    }
    Map<String, Object> generationConfig = new LinkedHashMap<>();
    generationConfig.put("temperature", 0.2);
    if (schema != null) {
      generationConfig.put("responseMimeType", "application/json");
      generationConfig.put("responseSchema", schema);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put(
        "systemInstruction", Map.of("parts", List.of(Map.of("text", command.systemInstruction()))));
    body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
    body.put("generationConfig", generationConfig);
    if (!command.toolSpecifications().isEmpty())
      body.put(
          "tools",
          List.of(
              Map.of(
                  "functionDeclarations",
                  command.toolSpecifications().stream()
                      .map(
                          tool ->
                              Map.of(
                                  "name",
                                  tool.providerName(),
                                  "description",
                                  tool.description(),
                                  "parameters",
                                  schema(tool.inputSchemaJson())))
                      .toList())));
    return body;
  }

  private Map<String, Object> continuationBody(
      RuntimeModelInvocationCommand command, JsonNode contents) {
    Map<String, Object> generationConfig = new LinkedHashMap<>();
    generationConfig.put("temperature", 0.2);
    if (command.structuredOutputRequired()) {
      generationConfig.put("responseMimeType", "application/json");
      generationConfig.put("responseSchema", outputSchema(command));
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put(
        "systemInstruction", Map.of("parts", List.of(Map.of("text", command.systemInstruction()))));
    body.put("contents", contents);
    body.put("generationConfig", generationConfig);
    body.put(
        "tools",
        List.of(
            Map.of(
                "functionDeclarations",
                command.toolSpecifications().stream()
                    .map(
                        tool ->
                            Map.of(
                                "name",
                                tool.providerName(),
                                "description",
                                tool.description(),
                                "parameters",
                                schema(tool.inputSchemaJson())))
                    .toList())));
    return body;
  }

  private void validate(
      RuntimeModelInvocationCommand command,
      ResolvedAiCredential credential,
      RuntimeBinaryInput binaryInput,
      boolean continuation) {
    if (command == null
        || credential == null
        || !ADAPTER_KEY.equals(command.adapterKey())
        || !"gemini".equals(command.providerKey())
        || command.connectTimeoutMs() < 100
        || command.timeoutMs() < command.connectTimeoutMs())
      throw new IllegalArgumentException("Gemini Runtime command is invalid");
    if (!command.providerKey().equals(credential.provider())
        || !command.modelKey().equals(credential.model())
        || !command.credentialSource().equals(credential.source().name()))
      throw new SecurityException("Gemini credential does not match Runtime command");
    validateTools(command);
    boolean imageRequired = command.modalities().contains("IMAGE");
    if (!continuation && (binaryInput == null) == imageRequired)
      throw new IllegalArgumentException("Gemini image modality and binary input do not match");
    if (binaryInput != null && !binaryInput.mediaType().startsWith("image/"))
      throw new IllegalArgumentException("Gemini Runtime accepts image binary input only");
  }

  private void validateTools(RuntimeModelInvocationCommand command) {
    if (command.allowedTools().size() != command.toolSpecifications().size()
        || !command.toolSpecifications().stream()
            .map(RuntimeToolSpecification::toolKey)
            .collect(java.util.stream.Collectors.toSet())
            .equals(command.allowedTools()))
      throw new IllegalArgumentException(
          "Gemini Runtime Tool specifications do not match allowlist");
  }

  private JsonNode schema(String value) {
    try {
      JsonNode schema = json.readTree(value);
      if (schema == null || !schema.isObject()) throw new IllegalArgumentException();
      return schema;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Gemini Runtime Tool schema is invalid", exception);
    }
  }

  private JsonNode outputSchema(RuntimeModelInvocationCommand command) {
    try {
      JsonNode schema = json.readTree(command.outputSchemaJson());
      if (schema == null || !schema.isObject() || schema.isEmpty())
        throw new IllegalArgumentException("Gemini structured output schema is invalid");
      return schema;
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Gemini structured output schema is invalid", exception);
    }
  }

  private URI endpoint(String value, String modelKey) {
    URI base = URI.create(value);
    if (!"https".equalsIgnoreCase(base.getScheme())
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null)
      throw new IllegalArgumentException("Gemini endpoint is invalid");
    String model = modelKey.startsWith("models/") ? modelKey.substring(7) : modelKey;
    if (!model.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,119}"))
      throw new IllegalArgumentException("Gemini model key is invalid");
    return URI.create(
        base.toString().replaceAll("/+$", "") + "/v1beta/models/" + model + ":generateContent");
  }

  private String content(JsonNode parsed, List<RuntimeToolCall> toolCalls) {
    StringBuilder result = new StringBuilder();
    JsonNode candidates = parsed.path("candidates");
    if (candidates.isArray())
      for (JsonNode candidate : candidates) {
        for (JsonNode part : candidate.path("content").path("parts")) {
          String text = part.path("text").asText("");
          if (!text.isBlank()) result.append(text);
        }
        if (!result.isEmpty()) return result.toString();
      }
    if (!toolCalls.isEmpty()) return "";
    if (!parsed.path("promptFeedback").path("blockReason").asText("").isBlank())
      throw failure("GEMINI_BLOCKED", null, false, null);
    throw failure("GEMINI_EMPTY", null, false, null);
  }

  private List<RuntimeToolCall> toolCalls(
      JsonNode parsed, List<RuntimeToolSpecification> specifications) {
    Map<String, String> names =
        specifications.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    RuntimeToolSpecification::providerName, RuntimeToolSpecification::toolKey));
    List<RuntimeToolCall> result = new ArrayList<>();
    int index = 0;
    for (JsonNode candidate : parsed.path("candidates"))
      for (JsonNode part : candidate.path("content").path("parts"))
        if (part.path("functionCall").isObject()) {
          JsonNode call = part.path("functionCall");
          String toolKey = names.get(call.path("name").asText());
          if (toolKey == null) throw failure("GEMINI_UNAUTHORIZED_TOOL_CALL", null, false, null);
          String responseId =
              parsed.path("responseId").asText("response").replaceAll("[^A-Za-z0-9_-]", "_");
          result.add(
              new RuntimeToolCall(
                  "gemini_" + responseId + "_" + index++,
                  toolKey,
                  call.path("args").isObject() ? call.path("args").toString() : "{}"));
        }
    return List.copyOf(result);
  }

  private String continuationState(JsonNode request, JsonNode response) {
    JsonNode content = response.path("candidates").path(0).path("content");
    if (!content.isObject()) throw failure("GEMINI_CONTINUATION_STATE_MISSING", null, false, null);
    var state = json.createObjectNode();
    var contents = state.putArray("contents");
    request.path("contents").forEach(contents::add);
    contents.add(content);
    try {
      return json.writeValueAsString(state);
    } catch (Exception exception) {
      throw failure("GEMINI_CONTINUATION_STATE_INVALID", null, false, exception);
    }
  }

  private String requestId(Map<String, List<String>> headers) {
    return headers.entrySet().stream()
        .filter(
            entry ->
                "x-request-id".equalsIgnoreCase(entry.getKey())
                    || "x-goog-request-id".equalsIgnoreCase(entry.getKey()))
        .flatMap(entry -> entry.getValue().stream())
        .findFirst()
        .filter(value -> value.length() <= 160)
        .orElse(null);
  }

  private boolean transientStatus(int status) {
    return status == 429 || status >= 500;
  }

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
