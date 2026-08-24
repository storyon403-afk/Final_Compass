package cn.finalscompass.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC 配置入口，集中声明哪些 API 必须登录以及哪些认证接口允许匿名访问 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final cn.finalscompass.service.AuthService auth;
  private final cn.finalscompass.service.SystemModuleService modules;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  /**
   * @param auth 注入给登录拦截器的认证服务
   */
  public WebConfig(cn.finalscompass.service.AuthService auth,cn.finalscompass.service.SystemModuleService modules,com.fasterxml.jackson.databind.ObjectMapper json) {
    this.auth = auth;
    this.modules=modules;this.json=json;
  }

  /** 注册全局 API 登录拦截规则 */
  @Override
  public void addArgumentResolvers(
      java.util.List<org.springframework.web.method.support.HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new cn.finalscompass.shared.security.AuthenticatedUserArgumentResolver(auth));
  }

  @Override
  public void addInterceptors(
      org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
    registry
        .addInterceptor(new AuthenticationInterceptor(auth))
        .order(100)
        .addPathPatterns("/api/**")
        .excludePathPatterns(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/beta-access/**",
            "/api/browser-bridge/tickets",
            "/api/system/health",
            "/api/ai-center/external-agent/**");
    registry.addInterceptor(new ModuleMaintenanceInterceptor(auth,modules,json))
        .order(200)
        .addPathPatterns("/api/**")
        .excludePathPatterns(
            "/api/system/modules/**",
            "/api/system/health",
            "/api/ai-center/external-agent/**");
  }
}
