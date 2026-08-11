package cn.finalscompass.service;

import cn.finalscompass.ai.credential.AiCredentialSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;

/** Enforces request frequency and platform-funded usage before external API calls. */
@Service
public class AiUsageGuardService {
    private final StringRedisTemplate redis;
    private final JdbcClient jdbc;
    private final String prefix;
    private final int perMinute;
    private final int platformDailyCalls;
    private final int platformMonthlyTokens;

    public AiUsageGuardService(StringRedisTemplate redis, JdbcClient jdbc,
            @Value("${app.environment:dev}") String environment,
            @Value("${app.ai.limits.calls-per-minute:6}") int perMinute,
            @Value("${app.ai.limits.platform-daily-calls:20}") int platformDailyCalls,
            @Value("${app.ai.limits.platform-monthly-tokens:100000}") int platformMonthlyTokens) {
        this.redis=redis; this.jdbc=jdbc; this.prefix="fc:"+environment+":ai-limit:";
        this.perMinute=perMinute; this.platformDailyCalls=platformDailyCalls; this.platformMonthlyTokens=platformMonthlyTokens;
    }

    public void check(long userId, AiCredentialSource source) {
        if (isAdministrator(userId)) return;
        increment(prefix + "minute:" + userId, perMinute, Duration.ofMinutes(1), "AI 请求过于频繁，请稍后再试");
        if (source != AiCredentialSource.PLATFORM) return;
        increment(prefix + "platform-day:" + LocalDate.now() + ":" + userId, platformDailyCalls,
                Duration.ofDays(2), "今日平台 AI 次数已用完，可以切换自己的 API Key");
        LocalDate start=YearMonth.now().atDay(1), end=YearMonth.now().plusMonths(1).atDay(1);
        int used=jdbc.sql("""
                SELECT COALESCE(SUM(input_units+output_units),0) FROM ai_usage_log
                WHERE user_id=:user AND credential_source='PLATFORM' AND status='SUCCEEDED'
                  AND created_at>=:start AND created_at<:end
                """).param("user",userId).param("start",start).param("end",end).query(Integer.class).single();
        if (used >= platformMonthlyTokens) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "本月平台 AI Token 额度已用完，可以切换自己的 API Key");
    }

    private boolean isAdministrator(long userId) {
        if (userId <= 0) return false;
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM app_user WHERE id=:user AND role='ADMIN' AND active=TRUE)")
                .param("user",userId).query(Boolean.class).single();
    }

    private void increment(String key,int limit,Duration ttl,String message) {
        Long count=redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key,ttl);
        if (count != null && count > limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,message);
    }
}
