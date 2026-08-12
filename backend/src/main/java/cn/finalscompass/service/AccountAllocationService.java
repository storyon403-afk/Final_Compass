package cn.finalscompass.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Allocates sequential beta login accounts using a single MySQL row lock. */
@Service
public class AccountAllocationService {
  private static final Logger log = LoggerFactory.getLogger(AccountAllocationService.class);
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  public AccountAllocationService(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  public String reserve(long requestId) {
    String value = transactions.execute(status -> reserveInTransaction(requestId));
    if (value == null)
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "内测账号分配失败");
    return value;
  }

  public void ensureVerifiedReservations() {
    List<Long> ids =
        jdbc.sql(
                """
                SELECT r.id FROM beta_access_request r
                LEFT JOIN account_reservation a ON a.request_id=r.id
                WHERE r.status='EMAIL_VERIFIED' AND a.id IS NULL
                ORDER BY r.verified_at,r.id LIMIT 200
                """)
            .query(Long.class)
            .list();
    for (Long id : ids) {
      try {
        reserve(id);
      } catch (RuntimeException exception) {
        // One temporarily failing request must not prevent later verified users from
        // receiving their reservations. A later list refresh retries the same row.
        log.warn(
            "Unable to backfill account reservation for request {}: {}",
            id,
            exception.getClass().getSimpleName());
      }
    }
  }

  public void consumeOrRelease(long requestId, String actualUsername) {
    jdbc.sql(
            """
            UPDATE account_reservation
            SET status=CASE WHEN reserved_username=:username THEN 'CONSUMED' ELSE 'RELEASED' END,
                consumed_at=CASE WHEN reserved_username=:username THEN NOW() ELSE NULL END
            WHERE request_id=:request AND status='RESERVED'
            """)
        .param("username", actualUsername)
        .param("request", requestId)
        .update();
  }

  private String reserveInTransaction(long requestId) {
    String requestStatus =
        jdbc.sql("SELECT status FROM beta_access_request WHERE id=:id FOR UPDATE")
            .param("id", requestId)
            .query(String.class)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("申请不存在"));
    List<String> existing =
        jdbc.sql("SELECT reserved_username FROM account_reservation WHERE request_id=:request")
            .param("request", requestId)
            .query(String.class)
            .list();
    if (!existing.isEmpty()) return existing.getFirst();
    if (!"EMAIL_VERIFIED".equals(requestStatus)) throw new IllegalArgumentException("申请尚未完成邮箱验证");
    Sequence sequence =
        jdbc.sql(
                """
                SELECT id,account_prefix,number_width,next_value FROM account_number_sequence
                WHERE active=TRUE ORDER BY id DESC LIMIT 1 FOR UPDATE
                """)
            .query(Sequence.class)
            .optional()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "未配置内测账号序列"));
    long number = sequence.nextValue();
    for (int attempt = 0; attempt < 100; attempt++, number++) {
      String candidate =
          sequence.accountPrefix() + String.format("%0" + sequence.numberWidth() + "d", number);
      boolean occupied =
          jdbc.sql(
                      """
SELECT (SELECT COUNT(*) FROM app_user WHERE username=:username)
     + (SELECT COUNT(*) FROM account_reservation WHERE reserved_username=:username)
""")
                  .param("username", candidate)
                  .query(Integer.class)
                  .single()
              > 0;
      jdbc.sql("UPDATE account_number_sequence SET next_value=:next WHERE id=:id")
          .param("next", number + 1)
          .param("id", sequence.id())
          .update();
      if (occupied) continue;
      jdbc.sql(
              """
INSERT INTO account_reservation(request_id,sequence_id,reserved_username,sequence_value)
VALUES (:request,:sequence,:username,:value)
""")
          .param("request", requestId)
          .param("sequence", sequence.id())
          .param("username", candidate)
          .param("value", number)
          .update();
      return candidate;
    }
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "连续账号空间不足，请检查编号配置");
  }

  private record Sequence(long id, String accountPrefix, int numberWidth, long nextValue) {}
}
