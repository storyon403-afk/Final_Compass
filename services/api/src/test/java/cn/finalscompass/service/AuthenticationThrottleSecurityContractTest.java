package cn.finalscompass.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthenticationThrottleSecurityContractTest {
  @Test
  void loginUsesAccountAndIpThrottleAroundPasswordVerification() throws IOException {
    String source = Files.readString(Path.of("src/main/java/cn/finalscompass/service/AuthService.java"));
    assertTrue(source.contains("throttle.loginKeys(request.username(), clientIp)"));
    assertTrue(source.contains("throttle.check(throttleKeys)"));
    assertTrue(source.contains("throttle.failed(throttleKeys)"));
    assertTrue(source.contains("throttle.succeeded(throttleKeys)"));
  }

  @Test
  void administratorReauthenticationUsesTheSharedThrottle() throws IOException {
    String source =
        Files.readString(Path.of("src/main/java/cn/finalscompass/service/MailAdminService.java"));
    assertTrue(source.contains("throttle.adminKeys(admin.id())"));
    assertTrue(source.contains("throttle.check(keys)"));
    assertTrue(source.contains("throttle.failed(keys)"));
    assertTrue(source.contains("throttle.succeeded(keys)"));
  }

  @Test
  void throttleUsesAtomicRedisBackoffWithoutRawIdentifiersInKeys() throws IOException {
    String source =
        Files.readString(
            Path.of("src/main/java/cn/finalscompass/service/AuthenticationThrottleService.java"));
    assertTrue(source.contains("DefaultRedisScript"));
    assertTrue(source.contains("math.min"));
    assertTrue(source.contains("login-account:\" + digest"));
    assertTrue(source.contains("login-ip:\" + digest"));
    assertTrue(source.contains("HttpStatus.TOO_MANY_REQUESTS"));
    assertFalse(source.contains("login-account:\" + normalize"));
  }
}
