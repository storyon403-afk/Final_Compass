package cn.finalscompass.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 基于 Redis 的验证密钥与原子请求限流 */
@Service
public class RedisVerificationService {
  private static final DefaultRedisScript<Long> LIMIT_SCRIPT =
      new DefaultRedisScript<>(
          """
          local n=redis.call('INCR',KEYS[1]);
          if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end;
          if n>tonumber(ARGV[2]) then return -1; end;
          return n;
          """,
          Long.class);
  private final StringRedisTemplate redis;
  private final String pepper;
  private final String prefix;

  public RedisVerificationService(
      StringRedisTemplate redis,
      @Value("${app.mail.verification-pepper:}") String pepper,
      @Value("${app.environment:dev}") String environment) {
    this.redis = redis;
    this.pepper = pepper == null ? "" : pepper;
    this.prefix = "fc:" + environment + ":auth:";
  }

  public void checkSendLimits(String email, String ip) {
    requireConfigured();
    String emailHash = digest(email);
    consume(prefix + "email-cooldown:" + emailHash + ":v1", 60, 1, "请60秒后再重新发送");
    consume(
        prefix + "email-daily:" + emailHash + ":" + LocalDate.now() + ":v1",
        86400,
        8,
        "该邮箱今日发送次数已达上限");
    String hour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    consume(prefix + "ip-hourly:" + digest(ip) + ":" + hour + ":v1", 3600, 15, "当前网络请求过于频繁");
  }

  public void store(long requestId, String email, String code) {
    requireConfigured();
    try {
      redis
          .opsForValue()
          .set(codeKey(requestId), hmac(requestId, email, code), Duration.ofMinutes(10));
      redis.delete(failKey(requestId));
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  public VerifyResult verify(long requestId, String email, String code) {
    requireConfigured();
    try {
      String expected = redis.opsForValue().get(codeKey(requestId));
      if (expected == null) return VerifyResult.EXPIRED;
      if (MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.UTF_8),
          hmac(requestId, email, code).getBytes(StandardCharsets.UTF_8))) {
        redis.delete(List.of(codeKey(requestId), failKey(requestId)));
        return VerifyResult.VALID;
      }
      Long failures = redis.opsForValue().increment(failKey(requestId));
      if (failures != null && failures == 1)
        redis.expire(failKey(requestId), Duration.ofMinutes(10));
      return failures != null && failures >= 5 ? VerifyResult.LOCKED : VerifyResult.INVALID;
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  public void discard(long requestId) {
    try {
      redis.delete(List.of(codeKey(requestId), failKey(requestId)));
    } catch (DataAccessException ignored) {
    }
  }

  private void consume(String key, int ttl, int limit, String message) {
    try {
      Long result =
          redis.execute(LIMIT_SCRIPT, List.of(key), Integer.toString(ttl), Integer.toString(limit));
      if (result == null) throw unavailable();
      if (result < 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  private void requireConfigured() {
    if (pepper.length() < 16)
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮箱验证服务尚未配置");
  }

  private String hmac(long requestId, String email, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal((requestId + ":" + email + ":" + code).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("验证码摘要生成失败", exception);
    }
  }

  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String codeKey(long id) {
    return prefix + "email-code:" + id + ":v1";
  }

  private String failKey(long id) {
    return prefix + "verify-fail:" + id + ":v1";
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮箱验证服务暂不可用");
  }

  public enum VerifyResult {
    VALID,
    INVALID,
    LOCKED,
    EXPIRED
  }
}
