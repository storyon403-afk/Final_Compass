package cn.finalscompass.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring MVC 配置入口，集中声明哪些 API 必须登录以及哪些认证接口允许匿名访问。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final cn.finalscompass.service.AuthService auth;

    /** @param auth 注入给登录拦截器的认证服务 */
    public WebConfig(cn.finalscompass.service.AuthService auth) { this.auth = auth; }

    /** 注册全局 API 登录拦截规则。 */
    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new AuthenticationInterceptor(auth))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/auth/beta-access/**", "/api/system/health");
    }
}
