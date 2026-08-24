package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import cn.finalscompass.ai.runtime.tool.RuntimeToolSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiResponsesRuntimeProviderClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sendsImageAndJsonSchemaAndParsesResponsesUsage() throws Exception {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200,
                Map.of("X-Request-Id", List.of("openai-request-1")),
                "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"answer\\\":42}\"}]}],"
                        + "\"usage\":{\"input_tokens\":15,\"output_tokens\":8}}"));
        var client = new OpenAiResponsesRuntimeProviderClient(json, transport);
        try (ResolvedAiCredential credential = credential();
             RuntimeBinaryInput image = new RuntimeBinaryInput("image/png", new byte[]{1, 2, 3})) {
            RuntimeProviderClientResult result = client.invoke(command(Set.of("TEXT", "IMAGE"), Set.of(), true),
                    credential, image);
            assertEquals("{\"answer\":42}", result.content());
            assertEquals(15, result.inputUnits());
            assertEquals(8, result.outputUnits());
            assertEquals("openai-request-1", result.providerRequestId());
        }
        assertEquals("https://gateway.example.com/openai/v1/responses", transport.request.uri().toString());
        assertEquals(1200, transport.request.connectTimeout().toMillis());
        assertEquals(24000, transport.request.requestTimeout().toMillis());
        assertFalse(transport.request.body().contains("test-secret"));
        JsonNode body = json.readTree(transport.request.body());
        JsonNode content = body.path("input").get(0).path("content");
        assertEquals("input_text", content.get(0).path("type").asText());
        assertEquals("input_image", content.get(1).path("type").asText());
        assertTrue(content.get(1).path("image_url").asText().startsWith("data:image/png;base64,"));
        assertEquals("json_schema", body.path("text").path("format").path("type").asText());
        assertTrue(body.path("text").path("format").path("strict").asBoolean());
        assertEquals("object", body.path("text").path("format").path("schema").path("type").asText());
    }

    @Test
    void rejectsMissingImageAndUnconfiguredToolsBeforeTransport() {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200, Map.of(), "{}"));
        var client = new OpenAiResponsesRuntimeProviderClient(json, transport);
        try (ResolvedAiCredential credential = credential()) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.invoke(command(Set.of("TEXT", "IMAGE"), Set.of(), false), credential, null));
            assertThrows(IllegalArgumentException.class,
                    () -> client.invoke(command(Set.of("TEXT"), Set.of("search"), false), credential, null));
        }
        assertEquals(0, transport.calls);
    }

    @Test
    void marksRateLimitRetryableWithoutLeakingResponseBody() {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(
                429, Map.of(), "sensitive provider details"));
        var client = new OpenAiResponsesRuntimeProviderClient(json, transport);
        RuntimeProviderClientException exception;
        try (ResolvedAiCredential credential = credential()) {
            exception = assertThrows(RuntimeProviderClientException.class,
                    () -> client.invoke(command(Set.of("TEXT"), Set.of(), false), credential, null));
        }
        assertEquals("OPENAI_HTTP_429", exception.errorCode());
        assertTrue(exception.retryable());
        assertFalse(exception.getMessage().contains("provider details"));
    }

    @Test
    void serializesPublishedToolSchemaAndReturnsAuthorizedToolCall() throws Exception {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200, Map.of(),
                "{\"id\":\"resp_123\",\"output\":[{\"type\":\"function_call\",\"call_id\":\"call_123\","
                        + "\"name\":\"Knowledge_search\",\"arguments\":\"{\\\"query\\\":\\\"limits\\\"}\"}]}"));
        var client = new OpenAiResponsesRuntimeProviderClient(json, transport);
        RuntimeToolSpecification tool = tool();
        try (ResolvedAiCredential credential = credential()) {
            RuntimeProviderClientResult result = client.invoke(
                    command(Set.of("TEXT"), Set.of("Knowledge.search"), false, List.of(tool)), credential, null);
            assertEquals("", result.content());
            assertEquals("Knowledge.search", result.toolCalls().getFirst().toolKey());
            assertEquals("call_123", result.toolCalls().getFirst().callId());
        }
        JsonNode declared = json.readTree(transport.request.body()).path("tools").get(0);
        assertEquals("function", declared.path("type").asText());
        assertEquals("Knowledge_search", declared.path("name").asText());
        assertTrue(declared.path("strict").asBoolean());
    }

    @Test
    void continuesWithFunctionOutputUsingOpaqueResponseId() throws Exception {
        SequencedTransport transport = new SequencedTransport(
                new RuntimeHttpResponse(200, Map.of(), "{\"id\":\"resp_1\",\"output\":[{"
                        + "\"type\":\"function_call\",\"call_id\":\"call_1\","
                        + "\"name\":\"Knowledge_search\",\"arguments\":\"{\\\"query\\\":\\\"x\\\"}\"}]}"),
                new RuntimeHttpResponse(200, Map.of(), "{\"id\":\"resp_2\",\"output_text\":\"final\","
                        + "\"usage\":{\"input_tokens\":4,\"output_tokens\":2}}"));
        var client = new OpenAiResponsesRuntimeProviderClient(json, transport);
        var command = command(Set.of("TEXT"), Set.of("Knowledge.search"), false, List.of(tool()));
        try (ResolvedAiCredential credential = credential()) {
            RuntimeProviderClientResult first = client.invoke(command, credential, null);
            RuntimeProviderClientResult result = client.continueWithToolResults(command, credential,
                    new RuntimeProviderContinuation(first.continuationState(), List.of(
                            new cn.finalscompass.ai.runtime.tool.RuntimeToolCallResult(
                                    "call_1", "Knowledge.search", true, "{\"matches\":[]}", null))));
            assertEquals("final", result.content());
            assertTrue(result.toolCalls().isEmpty());
        }
        JsonNode continuation = json.readTree(transport.requests.get(1).body());
        assertEquals("resp_1", continuation.path("previous_response_id").asText());
        assertEquals("function_call_output", continuation.path("input").get(0).path("type").asText());
        assertEquals("call_1", continuation.path("input").get(0).path("call_id").asText());
    }

    private RuntimeModelInvocationCommand command(Set<String> modalities, Set<String> tools,
                                                   boolean structured) {
        return command(modalities, tools, structured, List.of());
    }
    private RuntimeModelInvocationCommand command(Set<String> modalities, Set<String> tools,
                                                   boolean structured,
                                                   List<RuntimeToolSpecification> specifications) {
        return new RuntimeModelInvocationCommand(1, "openai", RuntimeProviderType.API, "openai-responses-v1",
                2, "gpt-test", 3, "default", "https://gateway.example.com/openai/", "PLATFORM",
                "course.help", "1.0.0", "system", "question", "{\"course\":1}", "output",
                "{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"number\"}},"
                        + "\"required\":[\"answer\"],\"additionalProperties\":false}",
                tools, specifications, modalities, structured, new BigDecimal("0.01"), new BigDecimal("0.02"),
                "USD", 1200, 24000);
    }
    private RuntimeToolSpecification tool() {
        return new RuntimeToolSpecification("Knowledge.search", "Knowledge_search", "Search knowledge",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},"
                        + "\"required\":[\"query\"],\"additionalProperties\":false}");
    }
    private ResolvedAiCredential credential() {
        return new ResolvedAiCredential("openai", "gpt-test", AiCredentialSource.PLATFORM,
                "test-secret-key".toCharArray());
    }
    private static final class RecordingTransport implements RuntimeHttpTransport {
        private final RuntimeHttpResponse response;
        private RuntimeHttpRequest request;
        private int calls;
        private RecordingTransport(RuntimeHttpResponse response) { this.response = response; }
        @Override public RuntimeHttpResponse postJson(RuntimeHttpRequest request) {
            this.request = request;
            calls++;
            return response;
        }
    }
    private static final class SequencedTransport implements RuntimeHttpTransport {
        private final ArrayDeque<RuntimeHttpResponse> responses;
        private final List<RuntimeHttpRequest> requests = new java.util.ArrayList<>();
        private SequencedTransport(RuntimeHttpResponse... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }
        @Override public RuntimeHttpResponse postJson(RuntimeHttpRequest request) {
            requests.add(request);
            return responses.removeFirst();
        }
    }
}
