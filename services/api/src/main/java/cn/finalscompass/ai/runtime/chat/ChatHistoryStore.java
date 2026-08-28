package cn.finalscompass.ai.runtime.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-backed bounded conversation history. History failures never make a model request fail. */
@Component
public final class ChatHistoryStore {
  private static final int HISTORY_LIMIT = 20;
  private static final Duration HISTORY_TTL = Duration.ofMinutes(120);

  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  public ChatHistoryStore(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  public List<Map<String, String>> load(long userId, String sessionKey) {
    try {
      String stored = redis.opsForValue().get(key(userId, sessionKey));
      if (stored == null || stored.isBlank()) return List.of();
      return json.readValue(stored, new TypeReference<List<Map<String, String>>>() {});
    } catch (Exception ignored) {
      return List.of();
    }
  }

  public void append(long userId, String sessionKey, String message, String answer) {
    try {
      List<Map<String, String>> history = new ArrayList<>(load(userId, sessionKey));
      history.add(Map.of("role", "user", "content", truncate(message, 4000)));
      history.add(Map.of("role", "assistant", "content", truncate(answer, 4000)));
      while (history.size() > HISTORY_LIMIT) history.remove(0);
      redis.opsForValue().set(key(userId, sessionKey), json.writeValueAsString(history), HISTORY_TTL);
    } catch (Exception ignored) {
      // Chat remains available when optional context persistence is unavailable.
    }
  }

  private static String key(long userId, String sessionKey) {
    return "fc:chat:" + userId + ":" + sessionKey;
  }

  private static String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }
}
