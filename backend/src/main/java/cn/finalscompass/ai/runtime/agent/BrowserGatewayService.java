package cn.finalscompass.ai.runtime.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/*
 * 维护流程图：
 *   WebSocket 注册 --> userId -> session
 *   sendCommand --> requestId -> CompletableFuture --> 浏览器
 *   浏览器回包 --> handleResult ---------------------> 唤醒等待方
 */
/**
 * 维护用户与浏览器扩展的 WebSocket 连接，并把异步命令和响应按 requestId 配对
 * 维护入口：浏览器命令协议或超时策略改这里；任务业务状态由 AiRuntimeDispatchService 维护
 */
@Service
public final class BrowserGatewayService {
  private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, CompletableFuture<Map<String, Object>>> pending =
      new ConcurrentHashMap<>();
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public BrowserGatewayService(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // 注册浏览器 WebSocket 会话。在结束时主动释放资源或擦除敏感数据；局部失败会降级为空结果，不让辅助能力中断主流程
  // 可升级：可增加结构化日志或监控指标，避免异常被完全吞掉
  public void register(long userId, WebSocketSession session) {
    WebSocketSession previous = sessions.put(userId, session);
    if (previous != null && previous.isOpen()) {
      try {
        previous.close();
      } catch (Exception ignored) {
      }
    }
  }

  public void unregister(WebSocketSession session) {
    sessions.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
  }

  public boolean isConnected(long userId) {
    WebSocketSession session = sessions.get(userId);
    return session != null && session.isOpen();
  }

  /**
   * 向已连接的浏览器发送命令并等待响应
   * 实现上，使用参数化 SQL 访问数据库，并将查询结果映射为领域对象；通过 Jackson 完成 JSON 的解析或序列化
   *
   * @param runKey 智能体任务唯一键
   * @param command 已经归一化的执行命令
   * @param params 发送给目标方法的参数
   * @param timeoutMs 允许等待的最长毫秒数
   * @return 处理后的业务结果
   */
  public Map<String, Object> sendCommand(
      String runKey, String command, Map<String, Object> params, long timeoutMs) {
    long userId =
        jdbc.sql("SELECT user_id FROM ai_runtime_run WHERE run_key=:key")
            .param("key", runKey)
            .query(Long.class)
            .optional()
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

  /**
   * 完成等待中的浏览器命令并传递成功或失败结果
   * 实现上，用异步结果对象关联请求与回包，并在超时后结束等待
   *
   * @param commandId command 对应的数据库 ID
   * @param status 目标状态
   * @param result 远端返回的执行结果
   * @param errorMessage 远端返回的错误信息
   */
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
