package cn.finalscompass.controller;

import cn.finalscompass.service.AiAnalysisService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiAnalysisController {
    private final AuthService auth;
    private final AiAnalysisService ai;

    public AiAnalysisController(AuthService auth, AiAnalysisService ai) {
        this.auth = auth;
        this.ai = ai;
    }

    @GetMapping("/dashboard")
    public AiAnalysisService.Dashboard dashboard(HttpServletRequest request) {
        return ai.dashboard(auth.current(request).id());
    }

    @PutMapping("/byok")
    public Map<String, Object> saveByok(HttpServletRequest request, @RequestBody AiAnalysisService.SaveUserKey body) {
        return ai.saveUserKey(auth.current(request).id(), body);
    }

    @DeleteMapping("/byok/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByok(HttpServletRequest request, @PathVariable String provider) {
        ai.deleteUserKey(auth.current(request).id(), provider);
    }

    @PutMapping("/admin/platform-key")
    public Map<String, Object> savePlatformKey(HttpServletRequest request, @RequestBody AiAnalysisService.SavePlatformKey body) {
        return ai.savePlatformKey(auth.requireAdmin(request).id(), body);
    }

    @PostMapping("/invoke")
    public AiAnalysisService.InvokeResult invoke(HttpServletRequest request, @RequestBody AiAnalysisService.InvokeRequest body) {
        return ai.invoke(auth.current(request).id(), body);
    }
}
