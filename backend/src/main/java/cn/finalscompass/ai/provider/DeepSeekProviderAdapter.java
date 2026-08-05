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

/** Real DeepSeek chat-completions adapter. It intentionally accepts text only. */
public final class DeepSeekProviderAdapter implements AiProviderAdapter {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public DeepSeekProviderAdapter(ObjectMapper json) { this.json = json; }
    @Override public String id() { return "deepseek"; }
    @Override public String displayName() { return "DeepSeek"; }
    @Override public Set<String> capabilities() { return Set.of("TEXT"); }

    @Override public AiProviderResult invoke(AiProviderRequest request, char[] apiKey) {
        if (request.image() != null) throw new IllegalArgumentException("DeepSeek 当前通道不接收原始图片，请选择支持视觉的 Provider");
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", request.model(), "stream", false, "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", request.plan().systemInstruction()),
                            Map.of("role", "user", "content", request.plan().userInput()))));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("https://api.deepseek.com/chat/completions"))
                    .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + new String(apiKey))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String,Object> parsed = json.readValue(response.body(), new TypeReference<>() {});
            if (response.statusCode() / 100 != 2) throw providerFailure("DEEPSEEK_" + response.statusCode());
            var choices = (List<?>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) throw providerFailure("DEEPSEEK_EMPTY");
            var message = (Map<?,?>) ((Map<?,?>) choices.getFirst()).get("message");
            String content = String.valueOf(message.get("content"));
            Map<?,?> usage = parsed.get("usage") instanceof Map<?,?> value ? value : Map.of();
            return new AiProviderResult(content, number(usage.get("prompt_tokens")), number(usage.get("completion_tokens")), false);
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw providerFailure("DEEPSEEK_REQUEST_FAILED"); }
    }

    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private ResponseStatusException providerFailure(String code) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DeepSeek 调用失败（" + code + "）");
    }
}
