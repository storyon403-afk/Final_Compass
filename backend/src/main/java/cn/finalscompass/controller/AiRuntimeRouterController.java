package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.routing.AiRuntimeRouterService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-center")
public final class AiRuntimeRouterController {
  private final AuthService auth;
  private final AiRuntimeRouterService router;

  public AiRuntimeRouterController(AuthService auth, AiRuntimeRouterService router) {
    this.auth = auth;
    this.router = router;
  }

  @GetMapping("/runtimes")
  public AiRuntimeRouterService.Catalog catalog(HttpServletRequest request) {
    auth.current(request);
    return router.catalog();
  }

  @PostMapping("/route")
  public AiRuntimeRouterService.RouteDecision route(
      HttpServletRequest request, @RequestBody AiRuntimeRouterService.RouteRequest body) {
    auth.current(request);
    return router.route(body);
  }
}
