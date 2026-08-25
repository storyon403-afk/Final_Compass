package cn.finalscompass.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedProxyServiceTest {
  @Test
  void acceptsForwardedAddressFromTrustedProxy() {
    var service = new TrustedProxyService("127.0.0.1,::1");
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "203.0.113.24");

    assertThat(service.clientIp(request)).isEqualTo("203.0.113.24");
  }

  @Test
  void ignoresSpoofedHeaderFromUntrustedClient() {
    var service = new TrustedProxyService("127.0.0.1,::1");
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("198.51.100.8");
    request.addHeader("X-Forwarded-For", "203.0.113.24");

    assertThat(service.clientIp(request)).isEqualTo("198.51.100.8");
  }

  @Test
  void rejectsMalformedForwardedAddress() {
    var service = new TrustedProxyService("127.0.0.1");
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "attacker.example");

    assertThat(service.clientIp(request)).isEqualTo("127.0.0.1");
  }

  @Test
  void rejectsHostnamesInTrustedProxyConfiguration() {
    assertThatThrownBy(() -> new TrustedProxyService("proxy.internal"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("literal IP addresses");
  }
}
