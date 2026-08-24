package cn.finalscompass.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 密码认证入口共享的暴力破解防护 */
@Service
public class AuthenticationThrottleService {
  private static final int FAILURE_WINDOW_SECONDS = 24 * 60 * 60;
  private static final DefaultRedisScript<Long> FAILURE_SCRIPT =
      new DefaultRedisScript<>(
          """
          local failures=redis.call('INCR',KEYS[1]);
          if failures==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end;
          if failures>=tonumber(ARGV[2]) then
            local exponent=failures-tonumber(ARGV[2]);
            local delay=math.min(tonumber(ARGV[3]),2^exponent);
            redis.call('SET',KEYS[2],'1','EX',delay);
            return delay;
          end;
          return 0;
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final String prefix;
  private final int failuresBeforeLock;
  private final int maximumLockSeconds;

  public AuthenticationThrottleService(
      StringRedisTemplate redis,
      @Value("${app.auth-throttle.failures-before-lock:5}") int failuresBeforeLock,
      @Value("${app.auth-throttle.maximum-lock-seconds:900}") int maximumLockSeconds,
      @Value("${app.environment:dev}") String environment) {
    this.redis = redis;
    this.prefix = "fc:" + environment + ":password-auth:";
    this.failuresBeforeLock = Math.max(1, failuresBeforeLock);
    this.maximumLockSeconds = Math.max(1, maximumLockSeconds);
  }

  public Keys loginKeys(String username, String ip) {
    return new Keys("login-account:" + digest(normalize(username)), "login-ip:" + digest(ip));
  }

  public Keys adminKeys(long userId) {
    return new Keys("admin:" + userId, null);
  }

  public void check(Keys keys) {
    try {
      if (Boolean.TRUE.equals(redis.hasKey(lockKey(keys.primary())))
          || (keys.secondary() != null
              && Boolean.TRUE.equals(redis.hasKey(lockKey(keys.secondary()))))) {
        throw throttled();
      }
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  public void failed(Keys keys) {
    boolean locked = recordFailure(keys.primary());
    if (keys.secondary() != null) locked = recordFailure(keys.secondary()) || locked;
    if (locked) throw throttled();
  }

  public void succeeded(Keys keys) {
    try {
      redis.delete(keySet(keys));
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  private boolean recordFailure(String key) {
    try {
      Long delay =
          redis.execute(
              FAILURE_SCRIPT,
              List.of(failureKey(key), lockKey(key)),
              Integer.toString(FAILURE_WINDOW_SECONDS),
              Integer.toString(failuresBeforeLock),
              Integer.toString(maximumLockSeconds));
      if (delay == null) throw unavailable();
      return delay > 0;
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  private List<String> keySet(Keys keys) {
    if (keys.secondary() == null)
      return List.of(failureKey(keys.primary()), lockKey(keys.primary()));
    return List.of(
        failureKey(keys.primary()),
        lockKey(keys.primary()),
        failureKey(keys.secondary()),
        lockKey(keys.secondary()));
  }

  private String failureKey(String key) {
    return prefix + key + ":failures:v1";
  }

  private String lockKey(String key) {
    return prefix + key + ":lock:v1";
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private ResponseStatusException throttled() {
    return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "密码尝试过于频繁，请稍后再试");
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "认证保护服务暂不可用");
  }

  public record Keys(String primary, String secondary) {}
}
