package cn.finalscompass.controller;

import cn.finalscompass.model.ApiModels.AnonymousProfile;
import cn.finalscompass.service.AnonymousIdentityService;
import cn.finalscompass.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityControllerTest {
    @Test
    void resolvesAnonymousIdentityFromAuthenticatedAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        var user = new AuthService.CurrentUser(42, "user01", "user01", "USER", "hash", "token", false);
        var expected = new AnonymousProfile("public-id", "银杏 0832");
        var identities = new StubIdentityService(expected);
        var auth = new StubAuthService(user);

        var result = new IdentityController(identities, auth).current(request);

        assertThat(result).isEqualTo(expected);
        assertThat(identities.requestedAccountId).isEqualTo(42);
    }

    private static final class StubIdentityService extends AnonymousIdentityService {
        private final AnonymousProfile result;
        private long requestedAccountId;
        private StubIdentityService(AnonymousProfile result) {
            super(null);
            this.result = result;
        }
        @Override public AnonymousProfile forAccount(long appUserId) {
            requestedAccountId = appUserId;
            return result;
        }
    }

    private static final class StubAuthService extends AuthService {
        private final CurrentUser user;
        private StubAuthService(CurrentUser user) {
            super(null);
            this.user = user;
        }
        @Override public CurrentUser current(jakarta.servlet.http.HttpServletRequest request) { return user; }
    }
}
