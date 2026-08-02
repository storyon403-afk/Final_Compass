package cn.finalscompass.service;

import cn.finalscompass.model.ApiModels.BetaAccessChallenge;
import cn.finalscompass.model.ApiModels.BetaAccessRequest;
import cn.finalscompass.model.ApiModels.BetaAccessVerification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Service
public class BetaAccessService {
    private final JdbcClient jdbc;
    private final SecureRandom random = new SecureRandom();

    public BetaAccessService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public BetaAccessChallenge request(BetaAccessRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String confirmation = request.confirmEmail().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "两次输入的邮箱不一致");
        }
        jdbc.sql("UPDATE beta_access_request SET status='EXPIRED' WHERE email=:email AND status='PENDING'")
                .param("email", email).update();
        String code = "%06d".formatted(random.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("""
            INSERT INTO beta_access_request(email,phone,verification_code,expires_at)
            VALUES (:email,:phone,:code,:expires)
            """).param("email", email).param("phone", request.phone().trim()).param("code", code)
                .param("expires", expiresAt).update(keyHolder, "id");
        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) throw new IllegalStateException("未能取得验证请求编号");
        long id = generatedKey.longValue();
        return new BetaAccessChallenge(id, email, expiresAt);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, String> verify(BetaAccessVerification request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        AccessRow row = jdbc.sql("""
            SELECT id,email,verification_code,status,expires_at,failed_attempts
            FROM beta_access_request WHERE id=:id AND email=:email FOR UPDATE
            """).param("id", request.requestId()).param("email", email).query(AccessRow.class).optional()
                .orElseThrow(() -> invalid("验证请求不存在，请重新申请"));
        if (!"PENDING".equals(row.status())) throw expired("该验证码已使用或已失效，请重新申请");
        if (row.expiresAt().isBefore(LocalDateTime.now())) {
            jdbc.sql("UPDATE beta_access_request SET status='EXPIRED' WHERE id=:id").param("id", row.id()).update();
            throw expired("验证码已过期，请重新申请");
        }
        if (row.failedAttempts() >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "尝试次数过多，请重新申请");
        if (!row.verificationCode().equals(request.code())) {
            jdbc.sql("UPDATE beta_access_request SET failed_attempts=failed_attempts+1 WHERE id=:id")
                    .param("id", row.id()).update();
            throw invalid("验证码不正确，请检查管理员发来的邮件");
        }
        jdbc.sql("UPDATE beta_access_request SET status='VERIFIED',verified_at=NOW() WHERE id=:id")
                .param("id", row.id()).update();
        return Map.of("status", "VERIFIED", "email", email);
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException expired(String message) {
        return new ResponseStatusException(HttpStatus.GONE, message);
    }

    private record AccessRow(long id, String email, String verificationCode, String status,
                             LocalDateTime expiresAt, int failedAttempts) {}
}
