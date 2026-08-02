package cn.finalscompass.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final cn.finalscompass.service.AuthService auth;

    public WebConfig(cn.finalscompass.service.AuthService auth) { this.auth = auth; }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new AuthenticationInterceptor(auth))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/auth/register", "/api/auth/beta-access/**", "/api/system/health");
    }
}
