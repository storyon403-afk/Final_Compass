package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.SystemModuleService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** 提供公共模块状态和管理员维护入口。 */
@RestController @RequestMapping("/api/system/modules")
public class SystemModuleController {
  private final AuthService auth; private final SystemModuleService modules;
  public SystemModuleController(AuthService auth,SystemModuleService modules){this.auth=auth;this.modules=modules;}
  @GetMapping public List<SystemModuleService.ModuleSetting> all(){return modules.all();}
  @PutMapping("/{key}") public SystemModuleService.ModuleSetting update(HttpServletRequest request,@PathVariable String key,@RequestBody SystemModuleService.Update input){return modules.update(auth.requireAdmin(request).id(),key,input);}
}
