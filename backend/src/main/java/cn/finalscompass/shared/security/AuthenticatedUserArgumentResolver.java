package cn.finalscompass.shared.security;

import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 注入当前账号且不向处理器泄漏 Servlet API 的 MVC 适配器 */
public final class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {
  private final AuthService auth;
  public AuthenticatedUserArgumentResolver(AuthService auth) { this.auth = auth; }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(Authenticated.class)
        && parameter.getParameterType().equals(AuthService.CurrentUser.class);
  }

  @Override
  public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
    return auth.current(webRequest.getNativeRequest(HttpServletRequest.class));
  }
}
