package cn.finalscompass.service;

import cn.finalscompass.ai.credential.AiCredentialSource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Enforces request frequency and platform-funded usage before external API calls. */
@Service
public class AiUsageGuardService {
  private final StringRedisTemplate redis;
  private final JdbcClient jdbc;
  private final String prefix;

  public AiUsageGuardService(
      StringRedisTemplate redis,
      JdbcClient jdbc,
      @org.springframework.beans.factory.annotation.Value("${app.environment:dev}") String environment) {
    this.redis = redis;
    this.jdbc = jdbc;
    this.prefix = "fc:" + environment + ":ai-limit:";
  }

  public void check(long userId, AiCredentialSource source) {
    if (isAdministrator(userId)) return;
    Policy policy = policy();
    if (!policy.enabled()) return;
    increment(prefix + "minute:" + userId, policy.perMinute(), Duration.ofMinutes(1), "AI 请求过于频繁，请稍后再试");
    if (source != AiCredentialSource.PLATFORM) return;
    increment(
        prefix + "platform-day:" + LocalDate.now() + ":" + userId,
        policy.dailyCalls(),
        Duration.ofDays(2),
        "今日平台 AI 次数已用完，可以切换自己的 API Key");
    LocalDate start = YearMonth.now().atDay(1), end = YearMonth.now().plusMonths(1).atDay(1);
    int used =
        jdbc.sql(
                """
                SELECT COALESCE(SUM(input_units+output_units),0) FROM ai_usage_log
                WHERE user_id=:user AND credential_source='PLATFORM' AND status='SUCCEEDED'
                  AND created_at>=:start AND created_at<:end
                """)
            .param("user", userId)
            .param("start", start)
            .param("end", end)
            .query(Integer.class)
            .single();
    if (used >= policy.monthlyTokens())
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "本月平台 AI Token 额度已用完，可以切换自己的 API Key");
  }

  public void record(long userId, String provider, String model, String skill,
      AiCredentialSource source, boolean succeeded, int inputUnits, int outputUnits,
      String errorCode, String traceId) {
    jdbc.sql("""
        INSERT INTO ai_usage_log(user_id,provider,model_name,skill_id,credential_source,status,
          input_units,output_units,error_code,trace_id,completed_at)
        VALUES(:user,:provider,:model,:skill,:source,:status,:input,:output,:error,:trace,NOW())
        """)
        .param("user", userId).param("provider", safe(provider, "unknown"))
        .param("model", safe(model, null)).param("skill", safe(skill, "UNKNOWN"))
        .param("source", source.name()).param("status", succeeded ? "SUCCEEDED" : "FAILED")
        .param("input", Math.max(0, inputUnits)).param("output", Math.max(0, outputUnits))
        .param("error", safe(errorCode, null)).param("trace", safe(traceId, null)).update();
  }

  private Policy policy() {
    return jdbc.sql("""
        SELECT qualified_user_limits_enabled AS enabled,calls_per_minute AS per_minute,
               platform_daily_calls AS daily_calls,platform_monthly_tokens AS monthly_tokens
        FROM platform_ai_setting WHERE id=1
        """).query(Policy.class).single();
  }

  private String safe(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value.length() > 120 ? value.substring(0, 120) : value;
  }

  private record Policy(boolean enabled, int perMinute, int dailyCalls, int monthlyTokens) {}

  private boolean isAdministrator(long userId) {
    if (userId <= 0) return false;
    return jdbc.sql(
            "SELECT EXISTS(SELECT 1 FROM app_user WHERE id=:user AND role='ADMIN' AND active=TRUE)")
        .param("user", userId)
        .query(Boolean.class)
        .single();
  }

  private void increment(String key, int limit, Duration ttl, String message) {
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1) redis.expire(key, ttl);
    if (count != null && count > limit)
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
  }
}
