package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.MailAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Administrator SMTP, template and manually confirmed credential-delivery endpoints. */
@RestController
@RequestMapping("/api/system/mail")
public class MailAdminController {
    private final AuthService auth;
    private final MailAdminService mail;
    public MailAdminController(AuthService auth, MailAdminService mail) { this.auth = auth; this.mail = mail; }

    @GetMapping("/smtp")
    public Map<String, Object> configuration(HttpServletRequest request) {
        auth.requireAdmin(request); return mail.configuration();
    }

    @PutMapping("/smtp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(HttpServletRequest request, @RequestBody MailAdminService.SmtpInput input) {
        mail.save(auth.requireAdmin(request), input);
    }

    @PostMapping("/smtp/test-and-enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(HttpServletRequest request, @RequestBody TestInput input) {
        mail.testAndEnable(auth.requireAdmin(request), input.adminPassword(), input.recipient());
    }

    @GetMapping("/templates")
    public List<Map<String, Object>> templates(HttpServletRequest request) {
        auth.requireAdmin(request); return mail.templates();
    }

    @PutMapping("/templates/{type}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void template(HttpServletRequest request, @PathVariable String type,
                         @RequestBody MailAdminService.TemplateInput input) {
        mail.updateTemplate(auth.requireAdmin(request), type.toUpperCase(), input);
    }

    @PostMapping("/beta-access/{id}/approve-and-send")
    public Map<String, String> approve(HttpServletRequest request, @PathVariable long id,
                                       @RequestBody MailAdminService.ProvisionInput input) {
        return mail.approveAndSend(auth.requireAdmin(request), id, input);
    }

    @GetMapping("/display-name-suggestion")
    public Map<String, String> displayNameSuggestion(HttpServletRequest request) {
        auth.requireAdmin(request);
        return mail.suggestDisplayName();
    }

    public record TestInput(String recipient, String adminPassword) {}
}
