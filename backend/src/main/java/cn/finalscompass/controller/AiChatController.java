package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.chat.AiChatService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai-center/chat")
public final class AiChatController {
  private final AuthService auth;
  private final AiChatService chat;

  public AiChatController(AuthService auth, AiChatService chat) {
    this.auth = auth;
    this.chat = chat;
  }

  @PostMapping("/sessions")
  public Map<String, String> createSession(HttpServletRequest request) {
    auth.current(request);
    return Map.of("sessionKey", chat.createSession());
  }

  @PostMapping(
      value = "/sessions/{sessionKey}/messages",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter message(
      HttpServletRequest request,
      @PathVariable String sessionKey,
      @RequestBody AiChatService.ChatRequest body) {
    AuthService.CurrentUser user = auth.current(request);
    SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
    Thread.ofVirtual()
        .name("ai-chat-" + sessionKey)
        .start(() -> chat.answer(user.id(), sessionKey, body, emitter));
    return emitter;
  }
}
