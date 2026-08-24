package cn.finalscompass.ai.runtime.provider.client;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeProviderClientContractTest {
    @Test
    void registryRejectsDuplicatesAndFailsClosedForMissingAdapter() {
        RuntimeProviderProtocolClient client = new StubClient("openai-responses-v1");
        RuntimeProviderClientRegistry registry = new RuntimeProviderClientRegistry(List.of(client));
        assertEquals(client, registry.require("openai-responses-v1"));
        assertThrows(IllegalStateException.class, () -> registry.require("unknown-adapter"));
        assertThrows(IllegalStateException.class,
                () -> new RuntimeProviderClientRegistry(List.of(client, client)));
    }

    @Test
    void binaryInputOwnsACopyAndClearsItOnClose() {
        byte[] source = {1, 2, 3};
        RuntimeBinaryInput input = new RuntimeBinaryInput("image/png", source);
        source[0] = 9;
        assertEquals(1, input.copyBytes()[0]);
        input.close();
        assertTrue(allZero(input.copyBytes()));
    }

    @Test
    void transportRejectsInsecureRemoteHttpHeaderInjectionAndUnboundedResponse() {
        JdkRuntimeHttpTransport transport = new JdkRuntimeHttpTransport();
        assertThrows(IllegalArgumentException.class, () -> transport.postJson(request(
                "http://api.example.com/v1", Map.of("Content-Type", "application/json"), 4096)));
        assertThrows(IllegalArgumentException.class, () -> transport.postJson(request(
                "https://api.example.com/v1", Map.of("X-Test", "safe\r\ninjected"), 4096)));
        assertThrows(IllegalArgumentException.class, () -> transport.postJson(request(
                "https://api.example.com/v1", Map.of(), 64 * 1024 * 1024)));
    }

    private RuntimeHttpRequest request(String uri, Map<String, String> headers, int maxBytes) {
        return new RuntimeHttpRequest(URI.create(uri), Duration.ofSeconds(1), Duration.ofSeconds(2),
                headers, "{}", maxBytes);
    }
    private boolean allZero(byte[] value) {
        for (byte item : value) if (item != 0) return false;
        return true;
    }
    private record StubClient(String adapterKey) implements RuntimeProviderProtocolClient {
        @Override public RuntimeProviderClientResult invoke(RuntimeModelInvocationCommand command,
                ResolvedAiCredential credential, RuntimeBinaryInput binaryInput) {
            return new RuntimeProviderClientResult("ok", 0, 0, false, null, List.of(), null);
        }
    }
}
