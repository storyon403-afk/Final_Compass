package cn.finalscompass.shared.security;

import cn.finalscompass.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 接口迁移到声明式安全期间使用的集中式应用授权策略 */
@Component
public class AuthorizationPolicy {
  public AuthService.CurrentUser requireAdmin(AuthService.CurrentUser user) {
    if (!user.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以执行此操作");
    }
    return user;
  }
}
