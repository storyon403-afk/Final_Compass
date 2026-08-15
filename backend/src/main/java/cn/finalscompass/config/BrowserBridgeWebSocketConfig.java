package cn.finalscompass.config;

import cn.finalscompass.service.BrowserBridgeCredentialService;
import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Registers the browser bridge WebSocket endpoint; the handshake consumes a short-lived,
 * single-use machine ticket from the WebSocket subprotocol header.
 */
@Configuration
@EnableWebSocket
public class BrowserBridgeWebSocketConfig implements WebSocketConfigurer {
  private final BrowserBridgeWebSocketHandler handler;
  private final BrowserBridgeCredentialService credentials;
  private final String[] allowedOriginPatterns;

  public BrowserBridgeWebSocketConfig(
      BrowserBridgeWebSocketHandler handler,
      BrowserBridgeCredentialService credentials,
      @Value("${app.browser-bridge.allowed-origin-patterns}") String allowedOriginPatterns) {
    this.handler = handler;
    this.credentials = credentials;
    this.allowedOriginPatterns =
        Arrays.stream(allowedOriginPatterns.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toArray(String[]::new);
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/ws/browser-bridge")
        .addInterceptors(
            new HandshakeInterceptor() {
              @Override
              public boolean beforeHandshake(
                  ServerHttpRequest request,
                  ServerHttpResponse response,
                  WebSocketHandler wsHandler,
                  Map<String, Object> attributes) {
                var userId = credentials.consume(ticketFrom(request));
                if (userId.isEmpty()) return false;
                attributes.put(BrowserBridgeWebSocketHandler.USER_ID_ATTRIBUTE, userId.getAsLong());
                return true;
              }

              @Override
              public void afterHandshake(
                  ServerHttpRequest request,
                  ServerHttpResponse response,
                  WebSocketHandler wsHandler,
                  Exception exception) {}

              private String ticketFrom(ServerHttpRequest request) {
                String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
                if (protocols == null) return null;
                for (String protocol : protocols.split(",")) {
                  String value = protocol.trim();
                  if (value.startsWith("ticket.")) return value.substring("ticket.".length());
                }
                return null;
              }
            })
        .setAllowedOriginPatterns(allowedOriginPatterns);
  }
}
