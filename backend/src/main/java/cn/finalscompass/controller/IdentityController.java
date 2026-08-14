package cn.finalscompass.controller;

import cn.finalscompass.model.ApiModels.AnonymousProfile;
import cn.finalscompass.service.AnonymousIdentityService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 为已认证账户取得或创建对外匿名身份，避免在社区功能中暴露账户信息。 */
@RestController
@RequestMapping("/api/identity")
public class IdentityController {
  private final AnonymousIdentityService identities;
  private final AuthService auth;

  public IdentityController(AnonymousIdentityService identities, AuthService auth) {
    this.identities = identities;
    this.auth = auth;
  }

  @PostMapping("/anonymous")
  public AnonymousProfile current(HttpServletRequest request) {
    return identities.forAccount(auth.current(request).id());
  }
}
