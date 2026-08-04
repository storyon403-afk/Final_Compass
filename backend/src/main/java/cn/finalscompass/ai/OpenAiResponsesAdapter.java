package cn.finalscompass.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** OpenAI Responses adapter with request-scoped image data URLs; no Files API upload is used. */
public final class OpenAiResponsesAdapter implements AiProviderAdapter {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    public OpenAiResponsesAdapter(ObjectMapper json) { this.json = json; }
    @Override public String id() { return "openai"; }
    @Override public String displayName() { return "OpenAI / GPT"; }
    @Override public Set<String> capabilities() { return Set.of("TEXT", "IMAGE"); }

    @Override public AiProviderResult invoke(AiProviderRequest request, char[] apiKey) {
        try {
            List<Map<String,Object>> content = new ArrayList<>();
            content.add(Map.of("type", "input_text", "text", request.plan().userInput()));
            if (request.image() != null) content.add(Map.of("type", "input_image", "detail", "high", "image_url",
                    "data:" + request.image().mediaType() + ";base64," + Base64.getEncoder().encodeToString(request.image().bytes())));
            String body = json.writeValueAsString(Map.of("model", request.model(),
                    "instructions", request.plan().systemInstruction(),
                    "input", List.of(Map.of("role", "user", "content", content)), "max_output_tokens", 1800));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + new String(apiKey))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String,Object> parsed = json.readValue(response.body(), new TypeReference<>() {});
            if (response.statusCode() / 100 != 2) throw failure("OPENAI_" + response.statusCode());
            String text = outputText(parsed);
            Map<?,?> usage = parsed.get("usage") instanceof Map<?,?> value ? value : Map.of();
            return new AiProviderResult(text, number(usage.get("input_tokens")), number(usage.get("output_tokens")), false);
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw failure("OPENAI_REQUEST_FAILED"); }
    }

    private String outputText(Map<String,Object> parsed) {
        if (parsed.get("output_text") instanceof String value && !value.isBlank()) return value;
        if (parsed.get("output") instanceof List<?> output) for (Object item : output) {
            if (!(item instanceof Map<?,?> map) || !(map.get("content") instanceof List<?> content)) continue;
            for (Object part : content) if (part instanceof Map<?,?> p && "output_text".equals(p.get("type"))) return String.valueOf(p.get("text"));
        }
        throw failure("OPENAI_EMPTY");
    }
    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private ResponseStatusException failure(String code) { return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI 调用失败（" + code + "）"); }
}
