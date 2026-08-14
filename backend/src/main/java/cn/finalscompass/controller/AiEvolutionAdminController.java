package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.evolution.AiEvolutionService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 提供 AI 演进报告刷新和建议审核等管理员操作。 */
@RestController
@RequestMapping("/api/system/ai-evolution")
public final class AiEvolutionAdminController {
  private final AuthService auth;
  private final AiEvolutionService evolution;

  public AiEvolutionAdminController(AuthService auth, AiEvolutionService evolution) {
    this.auth = auth;
    this.evolution = evolution;
  }

  @GetMapping
  public AiEvolutionService.Dashboard dashboard(HttpServletRequest request) {
    auth.requireAdmin(request);
    return evolution.dashboard();
  }

  @PostMapping("/refresh")
  public AiEvolutionService.RefreshResult refresh(
      HttpServletRequest request, @RequestParam(required = false) LocalDate date) {
    return evolution.refresh(
        auth.requireAdmin(request).id(), date == null ? LocalDate.now() : date);
  }

  @PostMapping("/recommendations/{id}/review")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void review(
      HttpServletRequest request,
      @PathVariable long id,
      @RequestBody AiEvolutionService.Review body) {
    evolution.review(auth.requireAdmin(request).id(), id, body);
  }
}
