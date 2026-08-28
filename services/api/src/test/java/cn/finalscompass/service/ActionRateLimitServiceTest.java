package cn.finalscompass.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class ActionRateLimitServiceTest {
  @Test
  void rejectsRequestAfterAtomicRedisLimitIsExceeded() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString())).thenReturn(-1L);
    var limits = new ActionRateLimitService(redis, 5, 30, 5, 10, "test");

    assertThatThrownBy(() -> limits.questionTopic(7))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("发布问题过于频繁");
    verify(redis).execute(any(RedisScript.class), eq(List.of("fc:test:action-limit:question-topic:user:7:v1")), eq("3600"), eq("5"));
  }
}
