package cn.finalscompass.ai.runtime.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Relays browser commands from the local Agent Gateway to the user's Chrome extension over WebSocket. */
@Service
public final class BrowserGatewayService {
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public BrowserGatewayService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void register(long userId, WebSocketSession session) {
        WebSocketSession previous = sessions.put(userId, session);
        if (previous != null && previous.isOpen()) {
            try { previous.close(); } catch (Exception ignored) {}
        }
    }

    public void unregister(WebSocketSession session) {
        sessions.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
    }

    public boolean isConnected(long userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    public Map<String, Object> sendCommand(String runKey, String command, Map<String, Object> params, long timeoutMs) {
        long userId = jdbc.sql("SELECT user_id FROM ai_runtime_run WHERE run_key=:key")
                .param("key", runKey).query(Long.class).optional()
                .orElseThrow(() -> new IllegalStateException("Agent run not found"));
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen())
            throw new IllegalStateException("浏览器扩展未连接，请在 Chrome 中启用扩展并登录");
        String commandId = UUID.randomUUID().toString();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pending.put(commandId, future);
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "COMMAND");
            message.put("commandId", commandId);
            message.put("runKey", runKey);
            message.put("command", command);
            message.put("params", params == null ? Map.of() : params);
            synchronized (session) {
                session.sendMessage(new TextMessage(json.writeValueAsString(message)));
            }
            Map<String, Object> result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result;
        } catch (java.util.concurrent.TimeoutException timeout) {
            pending.remove(commandId);
            throw new IllegalStateException("浏览器命令超时：" + command);
        } catch (IllegalStateException exception) {
            pending.remove(commandId);
            throw exception;
        } catch (Exception exception) {
            pending.remove(commandId);
            throw new IllegalStateException("浏览器命令下发失败：" + exception.getMessage());
        }
    }

    public void handleResult(String commandId, String status, Object result, String errorMessage) {
        CompletableFuture<Map<String, Object>> future = pending.remove(commandId);
        if (future == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("commandId", commandId);
        payload.put("status", status == null ? "FAILED" : status);
        payload.put("result", result);
        if (errorMessage != null) payload.put("error", errorMessage);
        future.complete(payload);
    }
}
