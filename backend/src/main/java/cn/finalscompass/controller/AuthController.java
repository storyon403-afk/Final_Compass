package cn.finalscompass.controller;

import cn.finalscompass.model.ApiModels.AuthProfile;
import cn.finalscompass.model.ApiModels.ChangePasswordRequest;
import cn.finalscompass.model.ApiModels.LoginRequest;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.BetaAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 处理注册、登录、密码变更及内测访问验证等账户认证流程 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  public static final String SESSION_COOKIE = "finals_compass_session";
  private final AuthService auth;
  private final BetaAccessService betaAccess;
  private final boolean sessionCookieSecure;

  public AuthController(
      AuthService auth,
      BetaAccessService betaAccess,
      @Value("${app.session-cookie-secure:false}") boolean sessionCookieSecure) {
    this.auth = auth;
    this.betaAccess = betaAccess;
    this.sessionCookieSecure = sessionCookieSecure;
  }

  @PostMapping("/beta-access/request")
  public cn.finalscompass.model.ApiModels.BetaAccessChallenge requestBetaAccess(
      HttpServletRequest servletRequest,
      @Valid @RequestBody cn.finalscompass.model.ApiModels.BetaAccessRequest request) {
    return betaAccess.request(request, clientIp(servletRequest));
  }

  @PostMapping("/beta-access/verify")
  public Map<String, String> verifyBetaAccess(
      @Valid @RequestBody cn.finalscompass.model.ApiModels.BetaAccessVerification request) {
    return betaAccess.verify(request);
  }

  @PostMapping("/login")
  public AuthProfile login(
      HttpServletRequest servletRequest,
      HttpServletResponse response,
      @Valid @RequestBody LoginRequest request) {
    AuthProfile profile = auth.login(request, clientIp(servletRequest));
    setSessionCookie(response, profile.token(), 7 * 24 * 60 * 60);
    return profile;
  }

  @PostMapping("/stream-cookie")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void streamCookie(HttpServletRequest request, HttpServletResponse response) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer "))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态无效");
    setSessionCookie(response, header.substring(7), 7 * 24 * 60 * 60);
  }

  @PostMapping("/register")
  public void register() {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "内测阶段暂不开放注册，请使用管理员分配的账号");
  }

  @PostMapping("/change-password")
  public Map<String, String> changePassword(
      HttpServletRequest servletRequest, @Valid @RequestBody ChangePasswordRequest request) {
    auth.changePassword(servletRequest, request.currentPassword(), request.newPassword());
    return Map.of("message", "密码修改成功");
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    auth.logout(request);
    setSessionCookie(response, "", 0);
  }

  private void setSessionCookie(HttpServletResponse response, String token, long maxAge) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(SESSION_COOKIE, token)
            .httpOnly(true)
            .secure(sessionCookieSecure)
            .sameSite("Lax")
            .path("/api")
            .maxAge(maxAge)
            .build()
            .toString());
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    return forwarded == null || forwarded.isBlank()
        ? request.getRemoteAddr()
        : forwarded.split(",", 2)[0].trim();
  }
}
