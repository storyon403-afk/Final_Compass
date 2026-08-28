package cn.finalscompass.config;

import cn.finalscompass.controller.AuthController;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 登录拦截器：从 Bearer 请求头或会话 Cookie 中提取令牌，并把当前用户放入请求属性。 Controller 后续通过 {@link
 * AuthService#current(HttpServletRequest)} 取得该用户
 */
public class AuthenticationInterceptor implements HandlerInterceptor {
  private final AuthService auth;

  /**
   * @param auth 负责校验会话令牌的认证服务
   */
  public AuthenticationInterceptor(AuthService auth) {
    this.auth = auth;
  }

  /**
   * 在 Controller 执行前完成身份验证；未登录时直接返回 401 JSON
   *
   * @return {@code true} 表示继续进入 Controller，{@code false} 表示响应已结束
   */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
    String header = request.getHeader("Authorization");
    String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    boolean cookieAuthenticated = false;
    if (token == null && request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if (AuthController.SESSION_COOKIE.equals(cookie.getName())) {
          token = cookie.getValue();
          cookieAuthenticated = true;
          break;
        }
      }
    }
    var user = auth.authenticate(token);
    if (user.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"账号登录已失效，请重新登录\"}");
      return false;
    }
    if (cookieAuthenticated && isStateChanging(request.getMethod()) && !validCsrf(request)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"请求安全校验失败，请刷新页面后重试\"}");
      return false;
    }
    request.setAttribute(AuthService.REQUEST_USER, user.get());
    if (user.get().mustChangePassword()
        && !request.getRequestURI().equals("/api/auth/change-password")
        && !request.getRequestURI().equals("/api/auth/logout")
        && !request.getRequestURI().equals("/api/identity/anonymous")) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"error\":\"首次登录必须先修改临时密码\"}");
      return false;
    }
    return true;
  }

  private boolean isStateChanging(String method) {
    return !("GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method));
  }

  private boolean validCsrf(HttpServletRequest request) {
    String cookieToken = null;
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if (AuthController.CSRF_COOKIE.equals(cookie.getName())) {
          cookieToken = cookie.getValue();
          break;
        }
      }
    }
    String headerToken = request.getHeader("X-CSRF-Token");
    return cookieToken != null
        && headerToken != null
        && MessageDigest.isEqual(
            cookieToken.getBytes(StandardCharsets.UTF_8),
            headerToken.getBytes(StandardCharsets.UTF_8));
  }
}
