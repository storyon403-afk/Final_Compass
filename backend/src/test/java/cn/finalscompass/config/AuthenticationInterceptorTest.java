package cn.finalscompass.config;

import cn.finalscompass.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
    void attachesAuthenticatedUserToRequest() throws Exception {
        var user = new AuthService.CurrentUser(7, "user01", "user01", "USER", "hash", "valid");
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
