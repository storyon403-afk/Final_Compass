package cn.finalscompass.config;

import cn.finalscompass.ai.runtime.agent.BrowserGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** WebSocket endpoint handler for the Chrome extension browser bridge. */
@Component
public final class BrowserBridgeWebSocketHandler extends TextWebSocketHandler {
  public static final String USER_ID_ATTRIBUTE = "browserBridgeUserId";

  private final BrowserGatewayService gateway;
  private final ObjectMapper json;

  public BrowserBridgeWebSocketHandler(BrowserGatewayService gateway, ObjectMapper json) {
    this.gateway = gateway;
    this.json = json;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Long userId = (Long) session.getAttributes().get(USER_ID_ATTRIBUTE);
    if (userId == null) {
      session.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    gateway.register(userId, session);
    synchronized (session) {
      session.sendMessage(
          new TextMessage(
              json.writeValueAsString(
                  Map.of("type", "HELLO", "userId", userId, "protocolVersion", "1.0"))));
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonNode node = json.readTree(message.getPayload());
    String type = node.path("type").asText("");
    if ("PING".equals(type)) {
      synchronized (session) {
        session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
      }
      return;
    }
    if ("RESULT".equals(type)) {
      String commandId = node.path("commandId").asText(null);
      if (commandId == null) return;
      gateway.handleResult(
          commandId,
          node.path("status").asText("FAILED"),
          node.has("result") ? json.convertValue(node.get("result"), Object.class) : null,
          node.path("error").asText(null));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    gateway.unregister(session);
  }
}
