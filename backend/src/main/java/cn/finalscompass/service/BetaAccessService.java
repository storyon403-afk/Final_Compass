package cn.finalscompass.service;

import cn.finalscompass.model.ApiModels.BetaAccessChallenge;
import cn.finalscompass.model.ApiModels.BetaAccessRequest;
import cn.finalscompass.model.ApiModels.BetaAccessVerification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/** Automated SMTP verification backed by Redis secrets and MySQL audit state. */
@Service
public class BetaAccessService {
    private static final Logger log = LoggerFactory.getLogger(BetaAccessService.class);
    private final JdbcClient jdbc;
    private final RedisVerificationService verification;
    private final DynamicMailService mail;
    private final AccountAllocationService accounts;
    private final SecureRandom random = new SecureRandom();

    public BetaAccessService(JdbcClient jdbc, RedisVerificationService verification, DynamicMailService mail,
                             AccountAllocationService accounts) {
        this.jdbc = jdbc; this.verification = verification; this.mail = mail; this.accounts = accounts;
    }

    public BetaAccessChallenge request(BetaAccessRequest request, String sourceIp) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(request.confirmEmail().trim().toLowerCase(Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "两次输入的邮箱不一致");
        verification.checkSendLimits(email, sourceIp == null ? "unknown" : sourceIp);
        jdbc.sql("UPDATE beta_access_request SET status='EXPIRED' WHERE email=:email AND status IN ('CREATED','CODE_SENT')")
                .param("email", email).update();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO beta_access_request(email,phone,verification_code,status,expires_at)
                VALUES (:email,:phone,NULL,'CREATED',:expires)
                """).param("email", email).param("phone", request.phone().trim()).param("expires", expiresAt).update(keys, "id");
        long id = keys.getKey().longValue();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        verification.store(id, email, code);
        try {
            mail.sendVerification(id, email, code);
            jdbc.sql("UPDATE beta_access_request SET status='CODE_SENT',last_code_sent_at=NOW() WHERE id=:id").param("id", id).update();
            return new BetaAccessChallenge(id, email, expiresAt);
        } catch (RuntimeException exception) {
            verification.discard(id);
            jdbc.sql("UPDATE beta_access_request SET status='EXPIRED' WHERE id=:id").param("id", id).update();
            throw exception;
        }
    }

    public Map<String, String> verify(BetaAccessVerification request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AccessRow row = jdbc.sql("SELECT id,email,status,expires_at,failed_attempts FROM beta_access_request WHERE id=:id AND email=:email")
                .param("id", request.requestId()).param("email", email).query(AccessRow.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证请求不存在，请重新申请"));
        // A successful verification is idempotent. This also covers the rare case where the
        // client lost the first response or account reservation was temporarily unavailable.
        if ("EMAIL_VERIFIED".equals(row.status())) {
            reserveEventually(row.id());
            return verified(email);
        }
        if (!"CODE_SENT".equals(row.status())) throw new ResponseStatusException(HttpStatus.GONE, "该验证码已使用或已失效，请重新申请");
        if (row.expiresAt().isBefore(LocalDateTime.now())) {
            verification.discard(row.id());
            jdbc.sql("UPDATE beta_access_request SET status='EXPIRED' WHERE id=:id").param("id", row.id()).update();
            throw new ResponseStatusException(HttpStatus.GONE, "验证码已过期，请重新申请");
        }
        switch (verification.verify(row.id(), email, request.code())) {
            case VALID -> {
                jdbc.sql("UPDATE beta_access_request SET status='EMAIL_VERIFIED',verified_at=NOW() WHERE id=:id AND status='CODE_SENT'")
                        .param("id", row.id()).update();
                // Email ownership is already proven. Reservation is recoverable and must not
                // turn a valid code into an apparent verification failure.
                reserveEventually(row.id());
                return verified(email);
            }
            case INVALID -> {
                jdbc.sql("UPDATE beta_access_request SET failed_attempts=failed_attempts+1 WHERE id=:id").param("id", row.id()).update();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码不正确，请检查邮件");
            }
            case LOCKED -> {
                jdbc.sql("UPDATE beta_access_request SET status='EXPIRED',failed_attempts=failed_attempts+1 WHERE id=:id").param("id", row.id()).update();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "尝试次数过多，请重新申请");
            }
            default -> {
                jdbc.sql("UPDATE beta_access_request SET status='EXPIRED' WHERE id=:id").param("id", row.id()).update();
                throw new ResponseStatusException(HttpStatus.GONE, "验证码已过期，请重新申请");
            }
        }
    }

    private void reserveEventually(long requestId) {
        try {
            accounts.reserve(requestId);
        } catch (RuntimeException exception) {
            log.warn("Verified beta request {} is awaiting account reservation: {}",
                    requestId, exception.getClass().getSimpleName());
        }
    }

    private Map<String, String> verified(String email) {
        return Map.of("status", "EMAIL_VERIFIED", "email", email);
    }

    private record AccessRow(long id, String email, String status, LocalDateTime expiresAt, int failedAttempts) {}
}
