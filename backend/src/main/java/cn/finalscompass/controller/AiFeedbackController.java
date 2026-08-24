package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.feedback.AiFeedbackService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 接收用户 AI 反馈，并提供反馈优化建议的管理员审核入口 */
@RestController
public final class AiFeedbackController {
  private final AuthService auth;
  private final AiFeedbackService feedback;

  public AiFeedbackController(AuthService auth, AiFeedbackService feedback) {
    this.auth = auth;
    this.feedback = feedback;
  }

  @PostMapping("/api/ai-center/feedback/offers")
  public AiFeedbackService.Offer offer(
      HttpServletRequest request, @RequestBody AiFeedbackService.OfferRequest body) {
    return feedback.offer(auth.current(request).id(), body);
  }

  @PostMapping("/api/ai-center/feedback")
  public AiFeedbackService.FeedbackResult submit(
      HttpServletRequest request, @RequestBody AiFeedbackService.SubmitRequest body) {
    return feedback.submit(auth.current(request).id(), body);
  }

  @DeleteMapping("/api/ai-center/feedback/offers/{key}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void dismiss(HttpServletRequest request, @PathVariable String key) {
    feedback.dismiss(auth.current(request).id(), key);
  }

  @GetMapping("/api/system/ai-feedback/optimization")
  public List<Map<String, Object>> queue(HttpServletRequest request) {
    auth.requireAdmin(request);
    return feedback.adminQueue();
  }

  @PostMapping("/api/system/ai-feedback/optimization/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void decide(
      HttpServletRequest request,
      @PathVariable long id,
      @RequestBody AiFeedbackService.Decision body) {
    feedback.decide(auth.requireAdmin(request).id(), id, body);
  }
}
