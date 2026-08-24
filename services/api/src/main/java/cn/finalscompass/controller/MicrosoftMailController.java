package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.MailAdminService;
import cn.finalscompass.service.MicrosoftGraphMailService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Microsoft Graph 委托邮件授权的管理员接口 */
@RestController
@RequestMapping("/api/system/mail/microsoft")
public class MicrosoftMailController {
  private final AuthService auth;
  private final MailAdminService admin;
  private final MicrosoftGraphMailService microsoft;
  private final String frontendOrigin;

  public MicrosoftMailController(
      AuthService auth,
      MailAdminService admin,
      MicrosoftGraphMailService microsoft,
      @Value("${app.mail.frontend-url:http://127.0.0.1:5173}") String frontendOrigin) {
    this.auth = auth;
    this.admin = admin;
    this.microsoft = microsoft;
    this.frontendOrigin = frontendOrigin;
  }

  @GetMapping
  public Map<String, Object> status(HttpServletRequest request) {
    auth.requireAdmin(request);
    return microsoft.status();
  }

  @PostMapping("/authorize")
  public Map<String, String> authorize(HttpServletRequest request) {
    return Map.of("authorizationUrl", microsoft.authorizationUrl(auth.requireAdmin(request)));
  }

  @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> callback(
      HttpServletRequest request, @RequestParam String code, @RequestParam String state) {
    microsoft.complete(auth.requireAdmin(request), code, state);
    String origin = frontendOrigin.replace("'", "");
    return ResponseEntity.ok(
        "<!doctype html><meta charset='utf-8'><title>Microsoft 邮箱已连接</title><p"
            + " style='font-family:sans-serif;padding:30px'>Microsoft 邮箱已连接，可以关闭此窗口。</p>"
            + "<script>if(window.opener){window.opener.postMessage({type:'fc-mail-oauth',status:'connected'},'"
            + origin
            + "');window.close()}</script>");
  }

  @PostMapping("/test-and-enable")
  public void test(HttpServletRequest request, @RequestBody ProtectedInput input) {
    var user = auth.requireAdmin(request);
    admin.verifyPassword(user, input.adminPassword());
    microsoft.testAndEnable(user, input.recipient());
  }

  @DeleteMapping
  public void disconnect(HttpServletRequest request, @RequestBody PasswordInput input) {
    var user = auth.requireAdmin(request);
    admin.verifyPassword(user, input.adminPassword());
    microsoft.disconnect(user);
  }

  public record ProtectedInput(String recipient, String adminPassword) {}

  public record PasswordInput(String adminPassword) {}
}
