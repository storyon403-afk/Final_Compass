package cn.finalscompass.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Issues a persistent machine binding once, then exchanges it for single-use short-lived tickets. */
@Service
public class BrowserBridgeCredentialService {
  private static final int TICKET_LIFETIME_SECONDS = 120;
  private final JdbcClient jdbc;
  private final SecureRandom random = new SecureRandom();

  public BrowserBridgeCredentialService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Binding bind(long userId) {
    String secret = randomToken();
    jdbc.sql(
            """
            INSERT INTO browser_bridge_binding(user_id,secret_hash)
            VALUES (:userId,:secretHash)
            ON DUPLICATE KEY UPDATE secret_hash=VALUES(secret_hash),created_at=NOW(),
              last_exchanged_at=NULL,revoked_at=NULL
            """)
        .param("userId", userId)
        .param("secretHash", hash(secret))
        .update();
    return new Binding(secret);
  }

  @Transactional
  public Ticket exchange(String bindingSecret) {
    if (bindingSecret == null || bindingSecret.isBlank()) throw invalidCredential();
    BindingRow binding =
        jdbc.sql(
                """
                SELECT id,user_id FROM browser_bridge_binding
                WHERE secret_hash=:secretHash AND revoked_at IS NULL
                """)
            .param("secretHash", hash(bindingSecret))
            .query(BindingRow.class)
            .optional()
            .orElseThrow(this::invalidCredential);
    String ticket = randomToken();
    LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(TICKET_LIFETIME_SECONDS);
    jdbc.sql(
            "INSERT INTO browser_bridge_ticket(binding_id,ticket_hash,expires_at)"
                + " VALUES (:bindingId,:ticketHash,:expiresAt)")
        .param("bindingId", binding.id())
        .param("ticketHash", hash(ticket))
        .param("expiresAt", expiresAt)
        .update();
    jdbc.sql("UPDATE browser_bridge_binding SET last_exchanged_at=NOW() WHERE id=:id")
        .param("id", binding.id())
        .update();
    jdbc.sql("DELETE FROM browser_bridge_ticket WHERE expires_at<=NOW() OR consumed_at IS NOT NULL")
        .update();
    return new Ticket(ticket, TICKET_LIFETIME_SECONDS);
  }

  @Transactional
  public java.util.OptionalLong consume(String ticket) {
    if (ticket == null || ticket.isBlank()) return java.util.OptionalLong.empty();
    var row =
        jdbc.sql(
                """
                SELECT t.id,b.user_id FROM browser_bridge_ticket t
                JOIN browser_bridge_binding b ON b.id=t.binding_id
                WHERE t.ticket_hash=:ticketHash AND t.expires_at>NOW()
                  AND t.consumed_at IS NULL AND b.revoked_at IS NULL
                FOR UPDATE
                """)
            .param("ticketHash", hash(ticket))
            .query(TicketRow.class)
            .optional();
    if (row.isEmpty()) return java.util.OptionalLong.empty();
    jdbc.sql("UPDATE browser_bridge_ticket SET consumed_at=NOW() WHERE id=:id")
        .param("id", row.get().id())
        .update();
    return java.util.OptionalLong.of(row.get().userId());
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private ResponseStatusException invalidCredential() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "浏览器扩展绑定凭证无效");
  }

  public record Binding(String bindingSecret) {}
  public record Ticket(String ticket, int expiresInSeconds) {}
  private record BindingRow(long id, long userId) {}
  private record TicketRow(long id, long userId) {}
}
