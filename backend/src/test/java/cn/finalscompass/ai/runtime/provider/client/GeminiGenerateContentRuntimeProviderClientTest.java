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

class GeminiGenerateContentRuntimeProviderClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sendsInlineImageAndSchemaAndParsesUsage() throws Exception {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200,
                Map.of("x-goog-request-id", List.of("gemini-request-1")),
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"answer\\\":42}\"}]}}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":11,\"candidatesTokenCount\":6}}"));
        var client = new GeminiGenerateContentRuntimeProviderClient(json, transport);
        try (ResolvedAiCredential credential = credential();
             RuntimeBinaryInput image = new RuntimeBinaryInput("image/jpeg", new byte[]{1, 2, 3})) {
            RuntimeProviderClientResult result = client.invoke(command(Set.of("TEXT", "IMAGE"), Set.of(), true),
                    credential, image);
            assertEquals("{\"answer\":42}", result.content());
            assertEquals(11, result.inputUnits());
            assertEquals(6, result.outputUnits());
            assertEquals("gemini-request-1", result.providerRequestId());
        }
        assertEquals("https://gateway.example.com/google/v1beta/models/gemini-test:generateContent",
                transport.request.uri().toString());
        assertFalse(transport.request.body().contains("test-secret"));
        assertTrue(transport.request.headers().containsKey("x-goog-api-key"));
        JsonNode body = json.readTree(transport.request.body());
        JsonNode parts = body.path("contents").get(0).path("parts");
        assertTrue(parts.get(1).path("inlineData").path("data").isTextual());
        assertEquals("image/jpeg", parts.get(1).path("inlineData").path("mimeType").asText());
        assertEquals("application/json",
                body.path("generationConfig").path("responseMimeType").asText());
        assertEquals("object", body.path("generationConfig").path("responseSchema").path("type").asText());
    }

    @Test
    void rejectsMismatchedImageAndToolsBeforeTransport() {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200, Map.of(), "{}"));
        var client = new GeminiGenerateContentRuntimeProviderClient(json, transport);
        try (ResolvedAiCredential credential = credential()) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.invoke(command(Set.of("TEXT", "IMAGE"), Set.of(), false), credential, null));
            assertThrows(IllegalArgumentException.class,
                    () -> client.invoke(command(Set.of("TEXT"), Set.of("browser"), false), credential, null));
        }
        assertEquals(0, transport.calls);
    }

    @Test
    void mapsRateLimitAndSafetyBlockWithoutLeakingProviderBody() {
        RecordingTransport rateLimit = new RecordingTransport(new RuntimeHttpResponse(
                429, Map.of(), "sensitive quota details"));
        var rateLimitClient = new GeminiGenerateContentRuntimeProviderClient(json, rateLimit);
        try (ResolvedAiCredential credential = credential()) {
            RuntimeProviderClientException exception = assertThrows(RuntimeProviderClientException.class,
                    () -> rateLimitClient.invoke(command(Set.of("TEXT"), Set.of(), false), credential, null));
            assertEquals("GEMINI_HTTP_429", exception.errorCode());
            assertTrue(exception.retryable());
            assertFalse(exception.getMessage().contains("quota details"));
        }

        RecordingTransport blocked = new RecordingTransport(new RuntimeHttpResponse(200, Map.of(),
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        var blockedClient = new GeminiGenerateContentRuntimeProviderClient(json, blocked);
        try (ResolvedAiCredential credential = credential()) {
            RuntimeProviderClientException exception = assertThrows(RuntimeProviderClientException.class,
                    () -> blockedClient.invoke(command(Set.of("TEXT"), Set.of(), false), credential, null));
            assertEquals("GEMINI_BLOCKED", exception.errorCode());
            assertFalse(exception.retryable());
        }
    }

    @Test
    void serializesFunctionDeclarationAndReturnsAuthorizedToolCall() throws Exception {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200, Map.of(),
                "{\"responseId\":\"response-7\",\"candidates\":[{\"content\":{\"parts\":[{"
                        + "\"functionCall\":{\"name\":\"Knowledge_search\","
                        + "\"args\":{\"query\":\"limits\"}}}]}}]}"));
        var client = new GeminiGenerateContentRuntimeProviderClient(json, transport);
        RuntimeToolSpecification tool = tool();
        try (ResolvedAiCredential credential = credential()) {
            RuntimeProviderClientResult result = client.invoke(
                    command(Set.of("TEXT"), Set.of("Knowledge.search"), false, List.of(tool)), credential, null);
            assertEquals("", result.content());
            assertEquals("Knowledge.search", result.toolCalls().getFirst().toolKey());
            assertTrue(result.toolCalls().getFirst().callId().startsWith("gemini_response-7_"));
        }
        JsonNode declaration = json.readTree(transport.request.body()).path("tools").get(0)
                .path("functionDeclarations").get(0);
        assertEquals("Knowledge_search", declaration.path("name").asText());
        assertEquals("object", declaration.path("parameters").path("type").asText());
    }

    @Test
    void continuesWithFunctionResponseAndPreservesConversationHistory() throws Exception {
        SequencedTransport transport = new SequencedTransport(
                new RuntimeHttpResponse(200, Map.of(), "{\"responseId\":\"r1\",\"candidates\":[{"
                        + "\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{"
                        + "\"name\":\"Knowledge_search\",\"args\":{\"query\":\"x\"}}}]}}]}"),
                new RuntimeHttpResponse(200, Map.of(), "{\"candidates\":[{\"content\":{"
                        + "\"parts\":[{\"text\":\"final\"}]}}]}"));
        var client = new GeminiGenerateContentRuntimeProviderClient(json, transport);
        var command = command(Set.of("TEXT"), Set.of("Knowledge.search"), false, List.of(tool()));
        try (ResolvedAiCredential credential = credential()) {
            RuntimeProviderClientResult first = client.invoke(command, credential, null);
            RuntimeProviderClientResult result = client.continueWithToolResults(command, credential,
                    new RuntimeProviderContinuation(first.continuationState(), List.of(
                            new cn.finalscompass.ai.runtime.tool.RuntimeToolCallResult(
                                    first.toolCalls().getFirst().callId(), "Knowledge.search", true,
                                    "{\"matches\":[]}", null))));
            assertEquals("final", result.content());
        }
        JsonNode contents = json.readTree(transport.requests.get(1).body()).path("contents");
        assertEquals(3, contents.size());
        assertEquals("model", contents.get(1).path("role").asText());
        assertEquals("user", contents.get(2).path("role").asText());
        assertEquals("Knowledge_search", contents.get(2).path("parts").get(0)
                .path("functionResponse").path("name").asText());
    }

    private RuntimeModelInvocationCommand command(Set<String> modalities, Set<String> tools,
                                                   boolean structured) {
        return command(modalities, tools, structured, List.of());
    }
    private RuntimeModelInvocationCommand command(Set<String> modalities, Set<String> tools,
                                                   boolean structured,
                                                   List<RuntimeToolSpecification> specifications) {
        return new RuntimeModelInvocationCommand(1, "gemini", RuntimeProviderType.API,
                "gemini-generate-content-v1", 2, "gemini-test", 3, "default",
                "https://gateway.example.com/google/", "PLATFORM", "course-help", "1.0.0",
                "system", "question", "{\"course\":1}", "output",
                "{\"type\":\"object\",\"properties\":{\"answer\":{\"type\":\"number\"}}}",
                tools, specifications, modalities, structured, new BigDecimal("0.01"), new BigDecimal("0.02"),
                "USD", 1300, 26000);
    }
    private RuntimeToolSpecification tool() {
        return new RuntimeToolSpecification("Knowledge.search", "Knowledge_search", "Search knowledge",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},"
                        + "\"required\":[\"query\"],\"additionalProperties\":false}");
    }
    private ResolvedAiCredential credential() {
        return new ResolvedAiCredential("gemini", "gemini-test", AiCredentialSource.PLATFORM,
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
