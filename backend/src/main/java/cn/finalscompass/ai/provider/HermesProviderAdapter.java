package cn.finalscompass.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** HTTP-only boundary to the independently deployed Hermes OpenAI-compatible API server. */
public final class HermesProviderAdapter implements AiProviderAdapter {
    private final ObjectMapper json;
    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public HermesProviderAdapter(ObjectMapper json, String baseUrl) {
        this.json = json;
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:8642" : baseUrl.trim();
        this.endpoint = URI.create(normalized.replaceAll("/+$", "") + "/v1/chat/completions");
    }

    @Override public String id() { return "hermes"; }
    @Override public String displayName() { return "Hermes Agent"; }
    @Override public Set<String> capabilities() { return Set.of("TEXT", "TOOLS", "AGENT"); }

    @Override public AiProviderResult invoke(AiProviderRequest request, char[] apiKey) {
        if (request.image() != null) throw new IllegalArgumentException("Hermes 当前接入仅支持文本任务");
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", request.model(), "stream", false,
                    // Hermes is the Agent Runtime: send the original user turn and
                    // let Hermes perform its own planning, skills and tool loop.
                    "messages", List.of(Map.of("role", "user", "content", request.plan().userInput()))));
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + new String(apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw failure("HERMES_" + response.statusCode());
            Map<String,Object> parsed = json.readValue(response.body(), new TypeReference<>() {});
            List<?> choices = parsed.get("choices") instanceof List<?> value ? value : List.of();
            if (choices.isEmpty() || !(choices.getFirst() instanceof Map<?,?> choice)
                    || !(choice.get("message") instanceof Map<?,?> message)) throw failure("HERMES_EMPTY");
            String content = String.valueOf(message.get("content"));
            Map<?,?> usage = parsed.get("usage") instanceof Map<?,?> value ? value : Map.of();
            return new AiProviderResult(content, number(usage.get("prompt_tokens")),
                    number(usage.get("completion_tokens")), false);
        } catch (ResponseStatusException exception) { throw exception; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw failure("HERMES_INTERRUPTED"); }
        catch (Exception exception) { throw failure("HERMES_REQUEST_FAILED"); }
    }

    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private ResponseStatusException failure(String code) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Hermes 调用失败（" + code + "）");
    }
}
