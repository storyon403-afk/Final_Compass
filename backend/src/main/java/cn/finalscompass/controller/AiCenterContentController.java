package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.content.AiCenterContentService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 提供 AI 中心内容查询，并限制内容维护操作仅管理员可用 */
@RestController
@RequestMapping("/api/ai-center/content")
public final class AiCenterContentController {
  private final AuthService auth;
  private final AiCenterContentService content;

  public AiCenterContentController(AuthService auth, AiCenterContentService content) {
    this.auth = auth;
    this.content = content;
  }

  @GetMapping("/{key}")
  public AiCenterContentService.Page page(HttpServletRequest request, @PathVariable String key) {
    auth.current(request);
    return content.published(key);
  }

  @PutMapping("/{key}")
  public AiCenterContentService.Page update(
      HttpServletRequest request,
      @PathVariable String key,
      @RequestBody AiCenterContentService.Update body) {
    return content.update(auth.requireAdmin(request).id(), key, body);
  }
}
