package cn.finalscompass.ai.runtime.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ChatHistoryStoreTest {
  @Test
  void appendsBoundedHistoryWithTtl() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("fc:chat:7:s1")).thenReturn("[]");
    ChatHistoryStore store = new ChatHistoryStore(redis, new ObjectMapper());

    store.append(7, "s1", "问题", "答案");

    verify(values).set(eq("fc:chat:7:s1"), any(String.class), eq(Duration.ofMinutes(120)));
  }

  @Test
  void returnsEmptyHistoryWhenRedisIsUnavailable() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new IllegalStateException("down"));
    assertThat(new ChatHistoryStore(redis, new ObjectMapper()).load(7, "s1")).isEqualTo(List.of());
  }
}
