package cn.finalscompass.controller;

import cn.finalscompass.service.AiAnalysisService;
import cn.finalscompass.service.AiDocumentConversionService;
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
    private final AiDocumentConversionService documents;

    public AiAnalysisController(AuthService auth, AiAnalysisService ai, AiDocumentConversionService documents) {
        this.auth = auth;
        this.ai = ai;
        this.documents = documents;
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

    @PutMapping("/admin/platform-default")
    public Map<String,Object> savePlatformDefault(HttpServletRequest request,
            @RequestBody AiAnalysisService.PlatformDefaultRequest body) {
        return ai.savePlatformDefault(auth.requireAdmin(request).id(), body);
    }

    @PostMapping("/invoke")
    public AiAnalysisService.InvokeResult invoke(HttpServletRequest request, @RequestBody AiAnalysisService.InvokeRequest body) {
        return ai.invoke(auth.current(request).id(), body);
    }

    /** Task-oriented API; execution is synchronous until the Redis worker phase is enabled. */
    @PostMapping("/tasks")
    public AiAnalysisService.InvokeResult createTask(HttpServletRequest request,
            @RequestBody AiAnalysisService.InvokeRequest body) {
        return ai.invoke(auth.current(request).id(), body);
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String,Object> task(HttpServletRequest request, @PathVariable long taskId) {
        return ai.task(auth.current(request).id(), taskId);
    }

    @GetMapping("/tasks/{taskId}/steps")
    public java.util.List<Map<String,Object>> taskSteps(HttpServletRequest request, @PathVariable long taskId) {
        return ai.taskSteps(auth.current(request).id(), taskId);
    }

    @PostMapping(value = "/attachments/convert", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiDocumentConversionService.ConversionResult convertAttachment(
            HttpServletRequest request, @RequestPart org.springframework.web.multipart.MultipartFile file) {
        auth.current(request);
        return documents.convert(file);
    }
}
