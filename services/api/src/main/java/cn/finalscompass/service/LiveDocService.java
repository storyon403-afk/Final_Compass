package cn.finalscompass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LiveDocService {
  public static final int MAX_PROJECT_BYTES = 100 * 1024 * 1024;
  public static final int MAX_EXPORT_BYTES = 8 * 1024 * 1024;
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final String rendererUrl;
  private final String rendererToken;

  public LiveDocService(JdbcClient jdbc, ObjectMapper json,
      @Value("${app.ai.pdf-renderer.url:}") String rendererUrl,
      @Value("${app.ai.pdf-renderer.token:}") String rendererToken) {
    this.jdbc = jdbc;
    this.json = json;
    this.rendererUrl = rendererUrl == null ? "" : rendererUrl.trim();
    this.rendererToken = rendererToken == null ? "" : rendererToken.trim();
  }

  public List<ProjectSummary> list(long userId) {
    return jdbc.sql("""
        SELECT id,name,document_kind,size_bytes,created_at,updated_at
        FROM livedoc_project WHERE user_id=:user ORDER BY updated_at DESC LIMIT 100
        """).param("user", userId).query(ProjectSummary.class).list();
  }

  public ProjectContent read(long userId, long id) {
    return jdbc.sql("""
        SELECT id,name,document_kind,content,size_bytes,created_at,updated_at
        FROM livedoc_project WHERE id=:id AND user_id=:user
        """).param("id", id).param("user", userId).query(ProjectContent.class).optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "liveDoc 项目不存在"));
  }

  @Transactional
  public ProjectSummary create(long userId, String name, String kind, byte[] content) {
    ValidProject project = validate(name, kind, content);
    jdbc.sql("""
        INSERT INTO livedoc_project(user_id,name,document_kind,content,size_bytes,content_digest)
        VALUES (:user,:name,:kind,:content,:size,:digest)
        """).param("user", userId).param("name", project.name()).param("kind", project.kind())
        .param("content", project.content()).param("size", project.content().length)
        .param("digest", digest(project.content())).update();
    Long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    return summary(userId, id);
  }

  @Transactional
  public ProjectSummary update(long userId, long id, String name, String kind, byte[] content) {
    ValidProject project = validate(name, kind, content);
    int changed = jdbc.sql("""
        UPDATE livedoc_project SET name=:name,document_kind=:kind,content=:content,
          size_bytes=:size,content_digest=:digest WHERE id=:id AND user_id=:user
        """).param("name", project.name()).param("kind", project.kind())
        .param("content", project.content()).param("size", project.content().length)
        .param("digest", digest(project.content())).param("id", id).param("user", userId).update();
    if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "liveDoc 项目不存在");
    return summary(userId, id);
  }

  @Transactional
  public void delete(long userId, long id) {
    if (jdbc.sql("DELETE FROM livedoc_project WHERE id=:id AND user_id=:user")
        .param("id", id).param("user", userId).update() == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "liveDoc 项目不存在");
    }
  }

  public byte[] renderPdf(String html) {
    byte[] source = String.valueOf(html).getBytes(StandardCharsets.UTF_8);
    if (source.length == 0 || source.length > MAX_EXPORT_BYTES) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "导出 HTML 为空或超过 8MB");
    }
    if (rendererUrl.isBlank() || rendererToken.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PDF 渲染服务未配置");
    }
    try {
      String body = json.writeValueAsString(Map.of("htmlBase64", Base64.getEncoder().encodeToString(source)));
      HttpRequest request = HttpRequest.newBuilder(URI.create(rendererUrl.replaceAll("/+$", "") + "/render"))
          .timeout(Duration.ofSeconds(65)).header("Content-Type", "application/json")
          .header("X-Renderer-Token", rendererToken)
          .POST(HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200 || response.body().length == 0) throw new IllegalStateException();
      return response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PDF 渲染被中断");
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PDF 渲染服务不可用");
    }
  }

  private ProjectSummary summary(long userId, long id) {
    return jdbc.sql("""
        SELECT id,name,document_kind,size_bytes,created_at,updated_at
        FROM livedoc_project WHERE id=:id AND user_id=:user
        """).param("id", id).param("user", userId).query(ProjectSummary.class).single();
  }

  static ValidProject validate(String name, String kind, byte[] content) {
    String safeKind = String.valueOf(kind).trim().toLowerCase();
    if (!safeKind.equals("vdocx") && !safeKind.equals("vpptx")) throw new IllegalArgumentException("项目类型必须是 vdocx 或 vpptx");
    String safeName = String.valueOf(name).trim();
    if (safeName.isBlank() || safeName.length() > 255) throw new IllegalArgumentException("项目名称不能为空且不能超过 255 字符");
    if (content == null || content.length == 0 || content.length > MAX_PROJECT_BYTES) throw new IllegalArgumentException("项目文件为空或超过 100MB");
    return new ValidProject(safeName, safeKind, content);
  }

  private static String digest(byte[] content) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
    catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
  }

  record ValidProject(String name, String kind, byte[] content) {}
  public record ProjectSummary(Long id, String name, String documentKind, Long sizeBytes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
  public record ProjectContent(Long id, String name, String documentKind, byte[] content, Long sizeBytes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
