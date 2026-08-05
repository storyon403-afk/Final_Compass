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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Google Gemini generateContent adapter with request-scoped inline image data. */
public final class GeminiGenerateContentAdapter implements AiProviderAdapter {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public GeminiGenerateContentAdapter(ObjectMapper json) { this.json = json; }
    @Override public String id() { return "gemini"; }
    @Override public String displayName() { return "Google / Gemini"; }
    @Override public Set<String> capabilities() { return Set.of("TEXT", "IMAGE"); }

    @Override public AiProviderResult invoke(AiProviderRequest request, char[] apiKey) {
        try {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", request.plan().userInput()));
            if (request.image() != null) parts.add(Map.of("inlineData", Map.of(
                    "mimeType", request.image().mediaType(),
                    "data", Base64.getEncoder().encodeToString(request.image().bytes()))));
            String body = json.writeValueAsString(Map.of(
                    "systemInstruction", Map.of("parts", List.of(Map.of("text", request.plan().systemInstruction()))),
                    "contents", List.of(Map.of("role", "user", "parts", parts)),
                    "generationConfig", Map.of("maxOutputTokens", 2200)));
            String model = normalizeModel(request.model());
            HttpRequest httpRequest = HttpRequest.newBuilder(
                            URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(60)).header("x-goog-api-key", new String(apiKey))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = sendWithTransientRetry(httpRequest);
            Map<String, Object> parsed = json.readValue(response.body(), new TypeReference<>() {});
            if (response.statusCode() / 100 != 2) throw failure("GEMINI_" + response.statusCode());
            String content = extractText(parsed);
            Map<?, ?> usage = parsed.get("usageMetadata") instanceof Map<?, ?> value ? value : Map.of();
            return new AiProviderResult(content, number(usage.get("promptTokenCount")),
                    number(usage.get("candidatesTokenCount")), false);
        } catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw failure("GEMINI_REQUEST_FAILED"); }
    }

    private HttpResponse<String> sendWithTransientRetry(HttpRequest request) throws Exception {
        long[] delays = {0, 1200, 2800};
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt < delays.length; attempt++) {
            if (delays[attempt] > 0) Thread.sleep(delays[attempt]);
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (!transientStatus(response.statusCode()) || attempt == delays.length - 1) return response;
        }
        return response;
    }

    private boolean transientStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private String normalizeModel(String value) {
        String model = value == null ? "" : value.trim();
        if (model.startsWith("models/")) model = model.substring("models/".length());
        if (!model.matches("[A-Za-z0-9._-]{2,100}")) throw new IllegalArgumentException("Gemini 模型名称不合法");
        return model;
    }

    private String extractText(Map<String, Object> parsed) {
        if (parsed.get("candidates") instanceof List<?> candidates) for (Object candidate : candidates) {
            if (!(candidate instanceof Map<?, ?> map) || !(map.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) continue;
            StringBuilder text = new StringBuilder();
            for (Object part : parts) if (part instanceof Map<?, ?> p && p.get("text") instanceof String value) text.append(value);
            if (!text.isEmpty()) return text.toString();
        }
        throw failure("GEMINI_EMPTY");
    }

    private int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private ResponseStatusException failure(String code) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini 调用失败（" + code + "）");
    }
}
