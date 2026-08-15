package cn.finalscompass.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BrowserBridgeSecurityContractTest {
  @Test
  void websocketUsesShortTicketHeaderAndConfiguredOrigins() throws IOException {
    String config =
        Files.readString(
            Path.of("src/main/java/cn/finalscompass/config/BrowserBridgeWebSocketConfig.java"));
    assertTrue(config.contains("Sec-WebSocket-Protocol"));
    assertTrue(config.contains("credentials.consume(ticketFrom(request))"));
    assertTrue(config.contains("setAllowedOriginPatterns(allowedOriginPatterns)"));
    assertFalse(config.contains("getParameter(\"token\")"));
    assertFalse(config.contains("setAllowedOriginPatterns(\"*\")"));
  }

  @Test
  void migrationSeparatesPersistentBindingFromSingleUseTicket() throws IOException {
    try (var stream =
        getClass()
            .getResourceAsStream("/db/migration/V62__browser_bridge_machine_binding.sql")) {
      assertNotNull(stream);
      String migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(migration.contains("CREATE TABLE browser_bridge_binding"));
      assertTrue(migration.contains("CREATE TABLE browser_bridge_ticket"));
      assertTrue(migration.contains("consumed_at TIMESTAMP NULL"));
      assertFalse(migration.contains("login_session"));
    }
  }
}
