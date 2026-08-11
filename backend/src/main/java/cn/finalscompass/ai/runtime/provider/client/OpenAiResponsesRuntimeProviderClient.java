package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import cn.finalscompass.ai.runtime.tool.RuntimeToolCall;
import cn.finalscompass.ai.runtime.tool.RuntimeToolSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class OpenAiResponsesRuntimeProviderClient implements RuntimeProviderProtocolClient {
    private static final String ADAPTER_KEY = "openai-responses-v1";
    private final ObjectMapper json;
    private final RuntimeHttpTransport transport;

    public OpenAiResponsesRuntimeProviderClient(ObjectMapper json, RuntimeHttpTransport transport) {
        this.json = json;
        this.transport = transport;
    }

    @Override public String adapterKey() { return ADAPTER_KEY; }

    @Override
    public RuntimeProviderClientResult invoke(RuntimeModelInvocationCommand command,
                                              ResolvedAiCredential credential,
                                              RuntimeBinaryInput binaryInput) {
        validate(command, credential, binaryInput, false);
        JsonNode outputSchema = command.structuredOutputRequired() ? outputSchema(command) : null;
        try {
            String body = json.writeValueAsString(requestBody(command, binaryInput, outputSchema));
            RuntimeHttpResponse response = transport.postJson(new RuntimeHttpRequest(
                    endpoint(command.baseUrl()), Duration.ofMillis(command.connectTimeoutMs()),
                    Duration.ofMillis(command.timeoutMs()),
                    Map.of("Authorization", "Bearer " + new String(credential.apiKey()),
                            "Content-Type", "application/json"),
                    body, 8 * 1024 * 1024));
            if (response.statusCode() / 100 != 2)
                throw failure("OPENAI_HTTP_" + response.statusCode(), response.statusCode(),
                        transientStatus(response.statusCode()), null);
            JsonNode parsed = json.readTree(response.body());
            JsonNode usage = parsed.path("usage");
            List<RuntimeToolCall> toolCalls = toolCalls(parsed, command.toolSpecifications());
            return new RuntimeProviderClientResult(content(parsed, toolCalls), usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0), false, requestId(response.headers()), toolCalls,
                    toolCalls.isEmpty() ? null : responseId(parsed));
        } catch (RuntimeProviderClientException exception) { throw exception; }
        catch (Exception exception) {
            throw failure("OPENAI_REQUEST_FAILED", null, timeout(exception), exception);
        }
    }

    @Override
    public RuntimeProviderClientResult continueWithToolResults(
            RuntimeModelInvocationCommand command, ResolvedAiCredential credential,
            RuntimeProviderContinuation continuation) {
        validate(command, credential, null, true);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", command.modelKey());
            body.put("previous_response_id", continuation.opaqueState());
            body.put("input", continuation.toolResults().stream().map(result -> Map.of(
                    "type", "function_call_output", "call_id", result.callId(),
                    "output", result.outputJson())).toList());
            if (!command.toolSpecifications().isEmpty()) body.put("tools", toolDeclarations(command));
            RuntimeHttpResponse response = transport.postJson(new RuntimeHttpRequest(
                    endpoint(command.baseUrl()), Duration.ofMillis(command.connectTimeoutMs()),
                    Duration.ofMillis(command.timeoutMs()),
                    Map.of("Authorization", "Bearer " + new String(credential.apiKey()),
                            "Content-Type", "application/json"),
                    json.writeValueAsString(body), 8 * 1024 * 1024));
            if (response.statusCode() / 100 != 2)
                throw failure("OPENAI_HTTP_" + response.statusCode(), response.statusCode(),
                        transientStatus(response.statusCode()), null);
            JsonNode parsed = json.readTree(response.body());
            List<RuntimeToolCall> calls = toolCalls(parsed, command.toolSpecifications());
            JsonNode usage = parsed.path("usage");
            return new RuntimeProviderClientResult(content(parsed, calls), usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0), false, requestId(response.headers()), calls,
                    calls.isEmpty() ? null : responseId(parsed));
        } catch (RuntimeProviderClientException exception) { throw exception; }
        catch (Exception exception) {
            throw failure("OPENAI_REQUEST_FAILED", null, timeout(exception), exception);
        }
    }

    private Map<String, Object> requestBody(RuntimeModelInvocationCommand command,
                                            RuntimeBinaryInput binaryInput,
                                            JsonNode outputSchema) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text",
                command.userInput() + "\n\n[Runtime Context]\n" + command.contextJson()));
        if (binaryInput != null) {
            byte[] bytes = binaryInput.copyBytes();
            try {
                content.add(Map.of("type", "input_image", "detail", "high", "image_url",
                        "data:" + binaryInput.mediaType() + ";base64," + Base64.getEncoder().encodeToString(bytes)));
            } finally { Arrays.fill(bytes, (byte) 0); }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", command.modelKey());
        body.put("instructions", command.systemInstruction());
        body.put("input", List.of(Map.of("role", "user", "content", content)));
        if (outputSchema != null) body.put("text", Map.of("format", Map.of(
                "type", "json_schema", "name", schemaName(command.skillKey()),
                "schema", outputSchema, "strict", true)));
        if (!command.toolSpecifications().isEmpty()) body.put("tools", toolDeclarations(command));
        return body;
    }

    private List<Map<String, Object>> toolDeclarations(RuntimeModelInvocationCommand command) {
        return command.toolSpecifications().stream().map(tool -> Map.of("type", "function",
                "name", tool.providerName(), "description", tool.description(),
                "parameters", schema(tool.inputSchemaJson()), "strict", true)).toList();
    }

    private void validate(RuntimeModelInvocationCommand command, ResolvedAiCredential credential,
                          RuntimeBinaryInput binaryInput, boolean continuation) {
        if (command == null || credential == null || !ADAPTER_KEY.equals(command.adapterKey())
                || !"openai".equals(command.providerKey()) || command.connectTimeoutMs() < 100
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

    private void validateTools(RuntimeModelInvocationCommand command) {
        if (command.allowedTools().size() != command.toolSpecifications().size()
                || !command.toolSpecifications().stream().map(RuntimeToolSpecification::toolKey)
                .collect(java.util.stream.Collectors.toSet()).equals(command.allowedTools()))
            throw new IllegalArgumentException("OpenAI Runtime Tool specifications do not match allowlist");
    }

    private JsonNode schema(String value) {
        try {
            JsonNode schema = json.readTree(value);
            if (schema == null || !schema.isObject()) throw new IllegalArgumentException();
            return schema;
        } catch (Exception exception) {
            throw new IllegalArgumentException("OpenAI Runtime Tool schema is invalid", exception);
        }
    }

    private JsonNode outputSchema(RuntimeModelInvocationCommand command) {
        try {
            JsonNode schema = json.readTree(command.outputSchemaJson());
            if (schema == null || !schema.isObject() || schema.isEmpty())
                throw new IllegalArgumentException("OpenAI structured output schema is invalid");
            return schema;
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) {
            throw new IllegalArgumentException("OpenAI structured output schema is invalid", exception);
        }
    }

    private URI endpoint(String value) {
        URI base = URI.create(value);
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null)
            throw new IllegalArgumentException("OpenAI endpoint is invalid");
        return URI.create(base.toString().replaceAll("/+$", "") + "/v1/responses");
    }

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

    private List<RuntimeToolCall> toolCalls(JsonNode parsed, List<RuntimeToolSpecification> specifications) {
        Map<String, String> names = specifications.stream().collect(java.util.stream.Collectors.toMap(
                RuntimeToolSpecification::providerName, RuntimeToolSpecification::toolKey));
        List<RuntimeToolCall> result = new ArrayList<>();
        for (JsonNode output : parsed.path("output")) if ("function_call".equals(output.path("type").asText())) {
            String toolKey = names.get(output.path("name").asText());
            if (toolKey == null) throw failure("OPENAI_UNAUTHORIZED_TOOL_CALL", null, false, null);
            result.add(new RuntimeToolCall(output.path("call_id").asText(), toolKey,
                    output.path("arguments").asText("{}")));
        }
        return List.copyOf(result);
    }

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
    private String requestId(Map<String, List<String>> headers) {
        return headers.entrySet().stream().filter(entry -> "x-request-id".equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream()).findFirst()
                .filter(value -> value.length() <= 160).orElse(null);
    }
    private boolean transientStatus(int status) { return status == 429 || status >= 500; }
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
