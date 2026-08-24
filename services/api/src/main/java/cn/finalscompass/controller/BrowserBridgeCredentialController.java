package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.BrowserBridgeCredentialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/browser-bridge")
public class BrowserBridgeCredentialController {
  private final AuthService auth;
  private final BrowserBridgeCredentialService credentials;

  public BrowserBridgeCredentialController(
      AuthService auth, BrowserBridgeCredentialService credentials) {
    this.auth = auth;
    this.credentials = credentials;
  }

  /** 需要用户明确操作，轮换绑定会使之前绑定的安装失效 */
  @PostMapping("/bindings")
  public BrowserBridgeCredentialService.Binding bind(HttpServletRequest request) {
    return credentials.bind(auth.current(request).id());
  }

  /** 由已绑定扩展调用，不使用用户登录会话 */
  @PostMapping("/tickets")
  public BrowserBridgeCredentialService.Ticket exchange(@Valid @RequestBody ExchangeRequest request) {
    return credentials.exchange(request.bindingSecret());
  }

  public record ExchangeRequest(@NotBlank String bindingSecret) {}
}
