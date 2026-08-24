package cn.finalscompass.controller;

import cn.finalscompass.model.ApiModels.Announcement;
import cn.finalscompass.model.ApiModels.UpdateAnnouncement;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.Authenticated;
import cn.finalscompass.system.application.SystemHandler;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 健康检查、公告与系统管理的 HTTP 适配器 */
@RestController
@RequestMapping("/api/system")
public class SystemController {
  private final SystemHandler handler;
  public SystemController(SystemHandler handler){this.handler=handler;}
  @GetMapping("/health") public Map<String,Object> health(){return handler.health();}
  @GetMapping("/announcement") public Announcement announcement(){return handler.announcement();}
  @PutMapping("/announcement") public Announcement updateAnnouncement(
      @Authenticated AuthService.CurrentUser user,@Valid @RequestBody UpdateAnnouncement input){
    return handler.updateAnnouncement(user,input);
  }
  @GetMapping("/metrics") public Map<String,Object> metrics(@Authenticated AuthService.CurrentUser user){return handler.metrics(user);}
  @GetMapping("/moderation") public List<Map<String,Object>> moderation(@Authenticated AuthService.CurrentUser user){return handler.moderation(user);}
  @GetMapping("/beta-access") public List<Map<String,Object>> betaAccess(@Authenticated AuthService.CurrentUser user){return handler.betaRequests(user);}
  @PostMapping("/moderation/{type}/{id}") public Map<String,String> moderate(
      @Authenticated AuthService.CurrentUser user,@PathVariable String type,@PathVariable long id,
      @RequestParam String decision){return handler.moderate(user,type,id,decision);}
  @DeleteMapping("/discussions/{id}") public Map<String,String> removeDiscussion(
      @Authenticated AuthService.CurrentUser user,@PathVariable long id){return handler.removeDiscussion(user,id);}
}
