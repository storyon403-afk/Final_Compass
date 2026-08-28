package cn.finalscompass.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 对会写入社区或消息数据的已登录操作执行 Redis 原子固定窗口限流。 */
@Service
public class ActionRateLimitService {
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
  private final String prefix;
  private final int topicsPerHour;
  private final int answersPerHour;
  private final int contactsPerHour;
  private final int broadcastsPerHour;

  public ActionRateLimitService(
      StringRedisTemplate redis,
      @Value("${app.action-limits.question-topics-per-hour:5}") int topicsPerHour,
      @Value("${app.action-limits.question-answers-per-hour:30}") int answersPerHour,
      @Value("${app.action-limits.admin-contacts-per-hour:5}") int contactsPerHour,
      @Value("${app.action-limits.admin-broadcasts-per-hour:10}") int broadcastsPerHour,
      @Value("${app.environment:dev}") String environment) {
    this.redis = redis;
    this.prefix = "fc:" + environment + ":action-limit:";
    this.topicsPerHour = positive(topicsPerHour);
    this.answersPerHour = positive(answersPerHour);
    this.contactsPerHour = positive(contactsPerHour);
    this.broadcastsPerHour = positive(broadcastsPerHour);
  }

  public void questionTopic(long userId) {
    consume("question-topic", userId, topicsPerHour, "发布问题过于频繁，请稍后再试");
  }

  public void questionAnswer(long userId) {
    consume("question-answer", userId, answersPerHour, "回答或回复过于频繁，请稍后再试");
  }

  public void contactAdmin(long userId) {
    consume("contact-admin", userId, contactsPerHour, "联系管理员过于频繁，请稍后再试");
  }

  public void adminBroadcast(long userId) {
    consume("admin-broadcast", userId, broadcastsPerHour, "全站广播过于频繁，请稍后再试");
  }

  private void consume(String action, long userId, int limit, String message) {
    try {
      Long result =
          redis.execute(
              LIMIT_SCRIPT,
              List.of(prefix + action + ":user:" + userId + ":v1"),
              "3600",
              Integer.toString(limit));
      if (result == null) throw unavailable();
      if (result < 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw unavailable();
    }
  }

  private int positive(int value) {
    return Math.max(1, value);
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "操作频率保护服务暂不可用");
  }
}
