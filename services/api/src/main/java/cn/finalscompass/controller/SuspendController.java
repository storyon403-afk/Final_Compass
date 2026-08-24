package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提供暂停页配置和视频读取，并限制配置及媒体维护操作仅管理员可用
 * 媒体文件只允许从专用暂停页目录写入和读取
 */
@RestController
@RequestMapping("/api/suspend")
public class SuspendController {
  private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm");
  private final JdbcClient jdbc;
  private final AuthService auth;
  private final Path videoDir;

  public SuspendController(
      JdbcClient jdbc, AuthService auth, @Value("${app.upload-dir}") String uploadDir) {
    this.jdbc = jdbc;
    this.auth = auth;
    this.videoDir = Path.of(uploadDir).toAbsolutePath().normalize().resolve("suspend");
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    Map<String, Object> setting =
        jdbc.sql("SELECT enabled,play_mode,fixed_video_id FROM suspend_setting WHERE id=1")
            .query()
            .singleRow();
    if (!Boolean.TRUE.equals(setting.get("enabled"))) return Map.of("enabled", false);
    String mode = String.valueOf(setting.get("play_mode"));
    Object fixedId = setting.get("fixed_video_id");
    List<Long> ids;
    if ("FIXED".equals(mode) && fixedId != null) {
      ids =
          jdbc.sql("SELECT id FROM suspend_video WHERE id=:id AND enabled=TRUE")
              .param("id", fixedId)
              .query(Long.class)
              .list();
    } else {
      ids =
          jdbc.sql("SELECT id FROM suspend_video WHERE enabled=TRUE ORDER BY id")
              .query(Long.class)
              .list();
    }
    return Map.of("enabled", true, "playMode", mode, "videoIds", ids);
  }

  @GetMapping("/admin")
  public Map<String, Object> admin(HttpServletRequest request) {
    auth.requireAdmin(request);
    return Map.of(
        "setting",
            jdbc.sql(
                    "SELECT enabled,play_mode,fixed_video_id,updated_at FROM suspend_setting WHERE"
                        + " id=1")
                .query()
                .singleRow(),
        "videos",
            jdbc.sql(
                    "SELECT"
                        + " id,display_name,content_type,size_bytes,duration_seconds,enabled,created_at"
                        + " FROM suspend_video ORDER BY created_at DESC")
                .query()
                .listOfRows());
  }

  @PutMapping("/admin/config")
  public Map<String, Object> update(HttpServletRequest request, @RequestBody UpdateSuspend input) {
    var admin = auth.requireAdmin(request);
    String mode = input.playMode() == null ? "FIXED" : input.playMode().toUpperCase();
    if (!Set.of("FIXED", "RANDOM").contains(mode))
      throw new IllegalArgumentException("播放方式只能是 FIXED 或 RANDOM");
    if (input.fixedVideoId() != null) {
      boolean exists =
          !jdbc.sql("SELECT id FROM suspend_video WHERE id=:id")
              .param("id", input.fixedVideoId())
              .query(Long.class)
              .list()
              .isEmpty();
      if (!exists) throw new IllegalArgumentException("所选视频不存在");
    }
    jdbc.sql(
            "UPDATE suspend_setting SET"
                + " enabled=:enabled,play_mode=:mode,fixed_video_id=:video,updated_by=:admin WHERE"
                + " id=1")
        .param("enabled", input.enabled())
        .param("mode", mode)
        .param("video", input.fixedVideoId())
        .param("admin", admin.id())
        .update();
    return admin(request);
  }

  @PostMapping("/admin/videos")
  public Map<String, Object> upload(
      HttpServletRequest request,
      @RequestParam MultipartFile file,
      @RequestParam int durationSeconds)
      throws IOException {
    var admin = auth.requireAdmin(request);
    if (file.isEmpty() || file.getSize() > 40L * 1024 * 1024)
      throw new IllegalArgumentException("视频不能为空且不得超过 40MB");
    String type = file.getContentType();
    if (!VIDEO_TYPES.contains(type)) throw new IllegalArgumentException("仅支持 MP4 或 WebM 视频");
    if (durationSeconds < 1 || durationSeconds > 30)
      throw new IllegalArgumentException("视频时长必须在 1 到 30 秒之间");
    Files.createDirectories(videoDir);
    String extension = "video/webm".equals(type) ? ".webm" : ".mp4";
    String storageName = UUID.randomUUID() + extension;
    Path destination = videoDir.resolve(storageName).normalize();
    if (!destination.startsWith(videoDir)) throw new IllegalArgumentException("文件路径不安全");
    file.transferTo(destination);
    try {
      jdbc.sql(
              "INSERT INTO"
                  + " suspend_video(display_name,storage_name,content_type,size_bytes,duration_seconds,uploaded_by)"
                  + " VALUES (:name,:storage,:type,:size,:duration,:admin)")
          .param("name", safeName(file.getOriginalFilename()))
          .param("storage", storageName)
          .param("type", type)
          .param("size", file.getSize())
          .param("duration", durationSeconds)
          .param("admin", admin.id())
          .update();
    } catch (RuntimeException error) {
      Files.deleteIfExists(destination);
      throw error;
    }
    return admin(request);
  }

  @GetMapping("/videos/{id}")
  public ResponseEntity<InputStreamResource> video(@PathVariable long id) throws IOException {
    VideoFile video =
        jdbc.sql(
                "SELECT storage_name,content_type,size_bytes FROM suspend_video WHERE id=:id AND"
                    + " enabled=TRUE")
            .param("id", id)
            .query(VideoFile.class)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("视频不存在或已停用"));
    Path path = videoDir.resolve(video.storageName()).normalize();
    if (!path.startsWith(videoDir) || !Files.isRegularFile(path))
      throw new IllegalArgumentException("视频文件不存在");
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
        .contentType(MediaType.parseMediaType(video.contentType()))
        .contentLength(video.sizeBytes())
        .body(new InputStreamResource(Files.newInputStream(path)));
  }

  private String safeName(String name) {
    if (name == null || name.isBlank()) return "暂挂视频";
    String cleaned = Path.of(name).getFileName().toString();
    return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
  }

  public record UpdateSuspend(boolean enabled, String playMode, Long fixedVideoId) {}

  public record VideoFile(String storageName, String contentType, long sizeBytes) {}
}
