package cn.finalscompass.config;

import cn.finalscompass.controller.AuthController;
import cn.finalscompass.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.http.Cookie;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationInterceptorTest {
    @Test
    void rejectsMissingSession() throws Exception {
        var interceptor = new AuthenticationInterceptor(new StubAuthService(Optional.empty()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("登录已失效");
    }

    @Test
    void requiresMatchingCsrfTokenForCookieAuthenticatedWrites() throws Exception {
        var user = new AuthService.CurrentUser(7, "user01", "user01", "USER", "hash", "valid", false);
        var interceptor = new AuthenticationInterceptor(new StubAuthService(Optional.of(user)));
        MockHttpServletRequest rejected = new MockHttpServletRequest("POST", "/api/messages/contact-admin");
        rejected.setCookies(new Cookie(AuthController.SESSION_COOKIE, "valid"));
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(rejected, rejectedResponse, new Object())).isFalse();
        assertThat(rejectedResponse.getStatus()).isEqualTo(403);

        MockHttpServletRequest accepted = new MockHttpServletRequest("POST", "/api/messages/contact-admin");
        accepted.setCookies(new Cookie(AuthController.SESSION_COOKIE, "valid"), new Cookie(AuthController.CSRF_COOKIE, "csrf-token"));
        accepted.addHeader("X-CSRF-Token", "csrf-token");
        assertThat(interceptor.preHandle(accepted, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void attachesAuthenticatedUserToRequest() throws Exception {
        var user = new AuthService.CurrentUser(7, "user01", "user01", "USER", "hash", "valid", false);
        var interceptor = new AuthenticationInterceptor(new StubAuthService(Optional.of(user)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute(AuthService.REQUEST_USER)).isSameAs(user);
    }

    private static final class StubAuthService extends AuthService {
        private final Optional<CurrentUser> result;
        private StubAuthService(Optional<CurrentUser> result) {
            super(null);
            this.result = result;
        }
        @Override public Optional<CurrentUser> authenticate(String token) { return result; }
    }
}
