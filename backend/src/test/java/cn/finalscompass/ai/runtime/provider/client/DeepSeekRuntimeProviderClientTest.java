package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekRuntimeProviderClientTest {
    @Test
    void usesDynamicEndpointTimeoutAndParsesUsageWithoutPuttingKeyInBody() throws Exception {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200,
                Map.of("x-request-id", List.of("request-123")),
                "{\"choices\":[{\"message\":{\"content\":\"answer\"}}],"
                        + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}"));
        DeepSeekRuntimeProviderClient client = new DeepSeekRuntimeProviderClient(new ObjectMapper(), transport);
        try (ResolvedAiCredential credential = credential()) {
            RuntimeProviderClientResult result = client.invoke(command(Set.of("TEXT")), credential, null);
            assertEquals("answer", result.content());
            assertEquals(12, result.inputUnits());
            assertEquals(7, result.outputUnits());
            assertEquals("request-123", result.providerRequestId());
        }
        assertEquals("https://proxy.example.com/api/chat/completions", transport.request.uri().toString());
        assertEquals(1500, transport.request.connectTimeout().toMillis());
        assertEquals(25000, transport.request.requestTimeout().toMillis());
        assertTrue(transport.request.headers().get("Authorization").startsWith("Bearer "));
        assertFalse(transport.request.body().contains("test-secret"));
        assertTrue(transport.request.body().contains("deepseek-test"));
        assertTrue(transport.request.body().contains("Runtime Context"));
    }

    @Test
    void marksTransientHttpFailureRetryableWithoutLeakingBody() {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(
                503, Map.of(), "provider internal details"));
        DeepSeekRuntimeProviderClient client = new DeepSeekRuntimeProviderClient(new ObjectMapper(), transport);
        RuntimeProviderClientException exception;
        try (ResolvedAiCredential credential = credential()) {
            exception = assertThrows(RuntimeProviderClientException.class,
                    () -> client.invoke(command(Set.of("TEXT")), credential, null));
        }
        assertEquals("DEEPSEEK_HTTP_503", exception.errorCode());
        assertEquals(503, exception.statusCode());
        assertTrue(exception.retryable());
        assertFalse(exception.getMessage().contains("internal details"));
    }

    @Test
    void rejectsVisionAndToolCallsBeforeTransport() {
        RecordingTransport transport = new RecordingTransport(new RuntimeHttpResponse(200, Map.of(), "{}"));
        DeepSeekRuntimeProviderClient client = new DeepSeekRuntimeProviderClient(new ObjectMapper(), transport);
        try (ResolvedAiCredential credential = credential();
             RuntimeBinaryInput image = new RuntimeBinaryInput("image/png", new byte[]{1})) {
            assertThrows(IllegalArgumentException.class,
                    () -> client.invoke(command(Set.of("TEXT", "IMAGE")), credential, image));
        }
        assertEquals(0, transport.calls);
    }

    private RuntimeModelInvocationCommand command(Set<String> modalities) {
        return new RuntimeModelInvocationCommand(1, "deepseek", RuntimeProviderType.API, "deepseek-chat-v1",
                2, "deepseek-test", 3, "default", "https://proxy.example.com/api/", "PLATFORM",
                "course-help", "1.0.0", "system", "question", "{\"course\":1}", "output", "{}",
                Set.of(), List.of(), modalities, false, new BigDecimal("0.01"), new BigDecimal("0.02"), "USD", 1500, 25000);
    }
    private ResolvedAiCredential credential() {
        return new ResolvedAiCredential("deepseek", "deepseek-test", AiCredentialSource.PLATFORM,
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
}
