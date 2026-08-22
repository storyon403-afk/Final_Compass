package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.LiveDocService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/livedoc")
public final class LiveDocController {
  private static final MediaType PROJECT_TYPE = MediaType.parseMediaType("application/vnd.vcp.vdoc+zip");
  private final AuthService auth;
  private final LiveDocService liveDoc;

  public LiveDocController(AuthService auth, LiveDocService liveDoc) { this.auth = auth; this.liveDoc = liveDoc; }

  @GetMapping("/projects")
  public List<LiveDocService.ProjectSummary> projects(HttpServletRequest request) {
    return liveDoc.list(auth.current(request).id());
  }

  @GetMapping("/projects/{id}")
  public ResponseEntity<byte[]> project(HttpServletRequest request, @PathVariable long id) {
    var project = liveDoc.read(auth.current(request).id(), id);
    return ResponseEntity.ok().contentType(PROJECT_TYPE).contentLength(project.sizeBytes())
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
            .filename(project.name(), StandardCharsets.UTF_8).build().toString())
        .body(project.content());
  }

  @PostMapping(value="/projects", consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public LiveDocService.ProjectSummary create(HttpServletRequest request, @RequestParam String name,
      @RequestParam String kind, @RequestBody byte[] content) {
    return liveDoc.create(auth.current(request).id(), name, kind, content);
  }

  @PutMapping(value="/projects/{id}", consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public LiveDocService.ProjectSummary update(HttpServletRequest request, @PathVariable long id,
      @RequestParam String name, @RequestParam String kind, @RequestBody byte[] content) {
    return liveDoc.update(auth.current(request).id(), id, name, kind, content);
  }

  @DeleteMapping("/projects/{id}")
  public void delete(HttpServletRequest request, @PathVariable long id) {
    liveDoc.delete(auth.current(request).id(), id);
  }

  @PostMapping(value="/export/pdf", consumes=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<byte[]> pdf(HttpServletRequest request, @RequestBody Map<String, String> body) {
    auth.current(request);
    byte[] pdf = liveDoc.renderPdf(body.getOrDefault("html", ""));
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(pdf.length).body(pdf);
  }
}
