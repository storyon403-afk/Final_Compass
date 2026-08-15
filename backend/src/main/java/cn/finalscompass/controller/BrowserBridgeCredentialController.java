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

  /** Explicit human action; rotating a binding invalidates the previously bound installation. */
  @PostMapping("/bindings")
  public BrowserBridgeCredentialService.Binding bind(HttpServletRequest request) {
    return credentials.bind(auth.current(request).id());
  }

  /** Called by the bound extension, never with a user's login session. */
  @PostMapping("/tickets")
  public BrowserBridgeCredentialService.Ticket exchange(@Valid @RequestBody ExchangeRequest request) {
    return credentials.exchange(request.bindingSecret());
  }

  public record ExchangeRequest(@NotBlank String bindingSecret) {}
}
