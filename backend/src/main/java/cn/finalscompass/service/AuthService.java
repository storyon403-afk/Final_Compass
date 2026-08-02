package cn.finalscompass.service;

import cn.finalscompass.model.ApiModels.AuthProfile;
import cn.finalscompass.model.ApiModels.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    public static final String REQUEST_USER = "finalsCompassUser";
    private final JdbcClient jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public AuthService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public AuthProfile login(LoginRequest request) {
        UserRow user = jdbc.sql("SELECT id,username,password_hash,display_name,role FROM app_user WHERE username=:username AND active=TRUE")
                .param("username", request.username().trim()).query(UserRow.class).optional()
                .orElseThrow(this::invalidCredentials);
        if (!passwords.matches(request.password(), user.passwordHash())) throw invalidCredentials();
        jdbc.sql("DELETE FROM login_session WHERE expires_at <= NOW()").update();
        String token = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO login_session(user_id,token,expires_at) VALUES (:user,:token,:expires)")
                .param("user", user.id()).param("token", token).param("expires", LocalDateTime.now().plusDays(7)).update();
        return new AuthProfile(token, user.username(), user.displayName(), user.role());
    }

    public Optional<CurrentUser> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return jdbc.sql("""
            SELECT u.id,u.username,u.password_hash,u.display_name,u.role
            FROM login_session s JOIN app_user u ON u.id=s.user_id
            WHERE s.token=:token AND s.expires_at>NOW() AND u.active=TRUE
            """).param("token", token).query(UserRow.class).optional()
                .map(row -> new CurrentUser(row.id(), row.username(), row.displayName(), row.role(), row.passwordHash(), token));
    }

    public CurrentUser current(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_USER);
        if (value instanceof CurrentUser user) return user;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    public CurrentUser requireAdmin(HttpServletRequest request) {
        CurrentUser user = current(request);
        if (!user.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以执行此操作");
        return user;
    }

    public void changePassword(HttpServletRequest request, String currentPassword, String newPassword) {
        CurrentUser user = current(request);
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误");
        }
        jdbc.sql("UPDATE app_user SET password_hash=:hash,password_changed_at=NOW() WHERE id=:id")
                .param("hash", passwords.encode(newPassword)).param("id", user.id()).update();
    }

    public void logout(HttpServletRequest request) {
        CurrentUser user = current(request);
        jdbc.sql("DELETE FROM login_session WHERE token=:token").param("token", user.token()).update();
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }

    private record UserRow(long id, String username, String passwordHash, String displayName, String role) {}
    public record CurrentUser(long id, String username, String displayName, String role, String passwordHash, String token) {
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }
}
