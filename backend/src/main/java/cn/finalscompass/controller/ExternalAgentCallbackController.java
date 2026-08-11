package cn.finalscompass.controller;

import cn.finalscompass.ai.runtime.agent.AiRuntimeDispatchService;
import cn.finalscompass.ai.runtime.agent.BrowserGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

/** Callback endpoints for the external local Agent Gateway; authenticated by per-run callback token. */
@RestController
@RequestMapping("/api/ai-center/external-agent")
public final class ExternalAgentCallbackController {
    private static final long MAX_ARTIFACT_BYTES = 100L * 1024 * 1024;

    private final AiRuntimeDispatchService dispatch;
    private final BrowserGatewayService browser;

    public ExternalAgentCallbackController(AiRuntimeDispatchService dispatch, BrowserGatewayService browser) {
        this.dispatch = dispatch;
        this.browser = browser;
    }

    public record StatusReport(String status, String summary, String errorCode) {}
    public record ArtifactUpload(String fileName, String contentType, String contentBase64) {}
    public record BrowserCommand(String command, Map<String, Object> params, Long timeoutMs) {}

    @GetMapping("/runs/{runKey}")
    public AiRuntimeDispatchService.Run run(HttpServletRequest request, @PathVariable String runKey) {
        long runId = authenticate(request, runKey);
        return runView(runId, runKey);
    }

    @PostMapping("/runs/{runKey}/status")
    public Map<String, Object> status(HttpServletRequest request, @PathVariable String runKey,
                                      @RequestBody StatusReport body) {
        long runId = authenticate(request, runKey);
        if (body == null || body.status() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        dispatch.updateStatus(runId, body.status(), body.summary(), body.errorCode());
        return Map.of("runKey", runKey, "status", body.status(), "accepted", true);
    }

    @PostMapping("/runs/{runKey}/artifacts")
    public Map<String, Object> artifact(HttpServletRequest request, @PathVariable String runKey,
                                        @RequestBody ArtifactUpload body) {
        long runId = authenticate(request, runKey);
        if (body == null || body.fileName() == null || body.fileName().isBlank()
                || body.contentBase64() == null || body.contentBase64().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact payload is required");
        String fileName = Paths.get(body.fileName()).getFileName().toString();
        byte[] content;
        try {
            content = Base64.getDecoder().decode(body.contentBase64());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact content is not valid base64");
        }
        if (content.length == 0 || content.length > MAX_ARTIFACT_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artifact size is invalid");
        try {
            Path dir = Paths.get("uploads", "agent-artifacts", runKey).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            Files.write(target, content);
            long artifactId = dispatch.addArtifact(runId, fileName,
                    body.contentType() == null || body.contentType().isBlank() ? "application/octet-stream" : body.contentType(),
                    target.toString(), content.length);
            return Map.of("artifactId", artifactId, "fileName", fileName, "sizeBytes", content.length);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store artifact");
        }
    }

    @PostMapping("/runs/{runKey}/browser/commands")
    public Map<String, Object> browserCommand(HttpServletRequest request, @PathVariable String runKey,
                                              @RequestBody BrowserCommand body) {
        authenticate(request, runKey);
        if (body == null || body.command() == null || body.command().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Browser command is required");
        long timeoutMs = body.timeoutMs() == null || body.timeoutMs() <= 0 ? 30_000 : Math.min(body.timeoutMs(), 120_000);
        return browser.sendCommand(runKey, body.command(), body.params(), timeoutMs);
    }

    private long authenticate(HttpServletRequest request, String runKey) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        try {
            return dispatch.authenticateCallback(runKey, token);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Callback token is invalid");
        }
    }

    private AiRuntimeDispatchService.Run runView(long runId, String runKey) {
        return dispatch.view(ownerOf(runId), runKey);
    }

    private long ownerOf(long runId) {
        return dispatch.ownerUserId(runId);
    }
}
