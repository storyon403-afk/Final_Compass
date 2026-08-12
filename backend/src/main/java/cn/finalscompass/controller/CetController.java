package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cet")
public class CetController {
  private static final Set<String> LEVELS = Set.of("CET4", "CET6");
  private static final Set<String> MODES = Set.of("PRACTICE", "INTENSIVE");
  private static final Set<String> ANSWER_TYPES = Set.of("CHOICE", "TEXT");
  private static final Set<String> AUDIO_EXTENSIONS =
      Set.of("mp3", "m4a", "wav", "ogg", "webm", "aac");
  private static final Set<String> PAPER_ASSET_TYPES = Set.of("question", "answer", "audio");

  private final JdbcClient jdbc;
  private final AuthService auth;
  private final Path uploadDir;

  public CetController(
      JdbcClient jdbc, AuthService auth, @Value("${app.upload-dir}") String uploadDir) {
    this.jdbc = jdbc;
    this.auth = auth;
    this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
  }

  @GetMapping("/papers")
  public List<CetPaper> papers(@RequestParam(required = false) String level) {
    String filter = level == null || level.isBlank() ? "" : "AND p.level=:level";
    var query =
        jdbc.sql(
            """
            SELECT p.id,p.level,p.exam_year,p.exam_month,p.set_number,p.title,p.published,
              a.source_name,a.source_page_url,a.usage_note,
              a.question_original_name,a.answer_original_name,a.audio_original_name
            FROM cet_paper p LEFT JOIN cet_paper_asset a ON a.paper_id=p.id
            WHERE p.published=TRUE %s
            ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number
            """
                .formatted(filter));
    if (!filter.isEmpty()) query = query.param("level", normalizedLevel(level));
    return query.query(PaperRow.class).list().stream()
        .map(
            row ->
                new CetPaper(
                    row.id(),
                    row.level(),
                    row.examYear(),
                    row.examMonth(),
                    row.setNumber(),
                    row.title(),
                    row.published(),
                    row.sourceName(),
                    row.sourcePageUrl(),
                    row.usageNote(),
                    row.questionOriginalName(),
                    row.answerOriginalName(),
                    row.audioOriginalName(),
                    paperAssetExists(row.id(), "question"),
                    paperAssetExists(row.id(), "answer"),
                    paperAssetExists(row.id(), "audio")))
        .toList();
  }

  @GetMapping("/papers/{id}/assets/{type}")
  public ResponseEntity<org.springframework.core.io.Resource> paperAsset(
      @PathVariable long id, @PathVariable String type) {
    PaperAssetFile asset = findPaperAsset(id, normalizedPaperAssetType(type));
    if (asset.storageName() == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该套卷尚未收录此资料");
    Path path = safePath(asset.storageName());
    if (!Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "本地资料文件不存在");
    String contentType = "application/pdf";
    if (type.equals("audio")) {
      try (InputStream input = Files.newInputStream(path)) {
        byte[] header = input.readNBytes(12);
        if (header.length >= 8
            && header[4] == 'f'
            && header[5] == 't'
            && header[6] == 'y'
            && header[7] == 'p') contentType = "audio/mp4";
        else contentType = Files.probeContentType(path);
      } catch (IOException ignored) {
        contentType = null;
      }
      if (contentType == null || !contentType.startsWith("audio/")) contentType = "audio/mp4";
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            org.springframework.http.ContentDisposition.inline()
                .filename(asset.originalName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new org.springframework.core.io.FileSystemResource(path));
  }

  @GetMapping("/items")
  public List<CetItem> items(
      @RequestParam String level,
      @RequestParam String mode,
      @RequestParam(required = false) String section) {
    String sectionFilter = section == null || section.isBlank() ? "" : "AND i.section=:section";
    var query =
        jdbc.sql(
                """
SELECT i.id,i.paper_id,p.level,p.exam_year,p.exam_month,p.set_number,p.title paper_title,
  i.mode,i.section,i.title,i.prompt,i.passage,i.translation,i.analysis,i.key_sentence,
  i.answer_type,i.options_json,i.correct_answer,i.item_order,
  CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
    THEN COALESCE(i.audio_original_name,a.audio_original_name)
    ELSE NULL END audio_original_name,
  i.audio_start_ms,i.audio_end_ms
FROM cet_item i JOIN cet_paper p ON p.id=i.paper_id
LEFT JOIN cet_paper_asset a ON a.paper_id=p.id
WHERE p.published=TRUE AND p.level=:level AND i.mode=:mode %s
ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number,i.item_order,i.id
"""
                    .formatted(sectionFilter))
            .param("level", normalizedLevel(level))
            .param("mode", normalizedMode(mode));
    if (!sectionFilter.isEmpty()) query = query.param("section", section.trim());
    return query.query(CetItem.class).list();
  }

  @PostMapping("/papers")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Long> createPaper(
      HttpServletRequest servletRequest, @Valid @RequestBody PaperInput input) {
    auth.requireAdmin(servletRequest);
    jdbc.sql(
            """
            INSERT INTO cet_paper(level,exam_year,exam_month,set_number,title,published)
            VALUES (:level,:year,:month,:setNumber,:title,TRUE)
            """)
        .param("level", normalizedLevel(input.level()))
        .param("year", input.examYear())
        .param("month", input.examMonth())
        .param("setNumber", input.setNumber())
        .param("title", input.title().trim())
        .update();
    return Map.of("id", jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single());
  }

  @PutMapping("/papers/{id}")
  public void updatePaper(
      HttpServletRequest servletRequest,
      @PathVariable long id,
      @Valid @RequestBody PaperInput input) {
    auth.requireAdmin(servletRequest);
    int changed =
        jdbc.sql(
                """
                UPDATE cet_paper SET level=:level,exam_year=:year,exam_month=:month,
                  set_number=:setNumber,title=:title WHERE id=:id
                """)
            .param("level", normalizedLevel(input.level()))
            .param("year", input.examYear())
            .param("month", input.examMonth())
            .param("setNumber", input.setNumber())
            .param("title", input.title().trim())
            .param("id", id)
            .update();
    if (changed == 0) throw notFound();
  }

  @DeleteMapping("/papers/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePaper(HttpServletRequest servletRequest, @PathVariable long id) {
    auth.requireAdmin(servletRequest);
    if (jdbc.sql("DELETE FROM cet_paper WHERE id=:id").param("id", id).update() == 0)
      throw notFound();
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Long> createItem(
      HttpServletRequest servletRequest, @Valid @RequestBody ItemInput input) {
    auth.requireAdmin(servletRequest);
    validateItem(input);
    jdbc.sql(
            """
INSERT INTO cet_item(paper_id,mode,section,title,prompt,passage,translation,analysis,
  key_sentence,answer_type,options_json,correct_answer,item_order,audio_start_ms,audio_end_ms)
VALUES (:paper,:mode,:section,:title,:prompt,:passage,:translation,:analysis,
  :keySentence,:answerType,CAST(:options AS JSON),:answer,:itemOrder,:startMs,:endMs)
""")
        .param("paper", input.paperId())
        .param("mode", input.mode())
        .param("section", input.section().trim())
        .param("title", input.title().trim())
        .param("prompt", input.prompt())
        .param("passage", input.passage())
        .param("translation", input.translation())
        .param("analysis", input.analysis())
        .param("keySentence", input.keySentence())
        .param("answerType", input.answerType())
        .param("options", options(input.optionsJson()))
        .param("answer", input.correctAnswer())
        .param("itemOrder", input.itemOrder())
        .param("startMs", input.audioStartMs())
        .param("endMs", input.audioEndMs())
        .update();
    return Map.of("id", jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single());
  }

  @PutMapping("/items/{id}")
  public void updateItem(
      HttpServletRequest servletRequest,
      @PathVariable long id,
      @Valid @RequestBody ItemInput input) {
    auth.requireAdmin(servletRequest);
    validateItem(input);
    int changed =
        jdbc.sql(
                """
UPDATE cet_item SET paper_id=:paper,mode=:mode,section=:section,title=:title,prompt=:prompt,
  passage=:passage,translation=:translation,analysis=:analysis,key_sentence=:keySentence,
  answer_type=:answerType,options_json=CAST(:options AS JSON),correct_answer=:answer,
  item_order=:itemOrder,audio_start_ms=:startMs,audio_end_ms=:endMs WHERE id=:id
""")
            .param("paper", input.paperId())
            .param("mode", input.mode())
            .param("section", input.section().trim())
            .param("title", input.title().trim())
            .param("prompt", input.prompt())
            .param("passage", input.passage())
            .param("translation", input.translation())
            .param("analysis", input.analysis())
            .param("keySentence", input.keySentence())
            .param("answerType", input.answerType())
            .param("options", options(input.optionsJson()))
            .param("answer", input.correctAnswer())
            .param("itemOrder", input.itemOrder())
            .param("startMs", input.audioStartMs())
            .param("endMs", input.audioEndMs())
            .param("id", id)
            .update();
    if (changed == 0) throw notFound();
  }

  @DeleteMapping("/items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteItem(HttpServletRequest servletRequest, @PathVariable long id)
      throws IOException {
    auth.requireAdmin(servletRequest);
    String storage =
        jdbc.sql("SELECT audio_storage_name FROM cet_item WHERE id=:id")
            .param("id", id)
            .query(String.class)
            .optional()
            .orElse(null);
    if (jdbc.sql("DELETE FROM cet_item WHERE id=:id").param("id", id).update() == 0)
      throw notFound();
    if (storage != null) Files.deleteIfExists(safePath(storage));
  }

  @PostMapping("/items/{id}/audio")
  public void uploadAudio(
      HttpServletRequest servletRequest, @PathVariable long id, @RequestPart MultipartFile file)
      throws IOException {
    auth.requireAdmin(servletRequest);
    if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频为空或超过 20MB");
    String original =
        StringUtils.cleanPath(
            file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename());
    String ext = StringUtils.getFilenameExtension(original);
    if (ext == null || !AUDIO_EXTENSIONS.contains(ext.toLowerCase()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 MP3、M4A、WAV、OGG、WebM 或 AAC");
    if (jdbc.sql("SELECT COUNT(*) FROM cet_item WHERE id=:id")
            .param("id", id)
            .query(Integer.class)
            .single()
        == 0) throw notFound();
    Files.createDirectories(uploadDir);
    String storage = "cet-" + UUID.randomUUID() + "." + ext.toLowerCase();
    file.transferTo(safePath(storage));
    jdbc.sql(
            """
UPDATE cet_item SET audio_storage_name=:storage,audio_original_name=:original,audio_mime_type=:mime WHERE id=:id
""")
        .param("storage", storage)
        .param("original", original)
        .param("mime", file.getContentType())
        .param("id", id)
        .update();
  }

  @GetMapping("/items/{id}/audio")
  public ResponseEntity<org.springframework.core.io.Resource> audio(@PathVariable long id) {
    AudioFile audio =
        jdbc.sql(
                """
SELECT CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
    THEN COALESCE(i.audio_storage_name,a.audio_storage_name) ELSE NULL END audio_storage_name,
  CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
    THEN COALESCE(i.audio_original_name,a.audio_original_name) ELSE NULL END audio_original_name,
  i.audio_mime_type
FROM cet_item i JOIN cet_paper p ON p.id=i.paper_id
LEFT JOIN cet_paper_asset a ON a.paper_id=p.id
WHERE i.id=:id
""")
            .param("id", id)
            .query(AudioFile.class)
            .optional()
            .orElseThrow(this::notFound);
    if (audio.audioStorageName() == null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该题尚未上传音频");
    Path path = safePath(audio.audioStorageName());
    if (!Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "音频文件不存在");
    MediaType type;
    String detectedType = audio.audioMimeType();
    if (detectedType == null) {
      try (InputStream input = Files.newInputStream(path)) {
        byte[] header = input.readNBytes(12);
        detectedType =
            header.length >= 8
                    && header[4] == 'f'
                    && header[5] == 't'
                    && header[6] == 'y'
                    && header[7] == 'p'
                ? "audio/mp4"
                : Files.probeContentType(path);
      } catch (IOException ignored) {
        detectedType = null;
      }
    }
    try {
      type = MediaType.parseMediaType(detectedType == null ? "audio/mpeg" : detectedType);
    } catch (IllegalArgumentException ignored) {
      type = MediaType.parseMediaType("audio/mpeg");
    }
    return ResponseEntity.ok()
        .contentType(type)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            org.springframework.http.ContentDisposition.inline()
                .filename(audio.audioOriginalName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new org.springframework.core.io.FileSystemResource(path));
  }

  private void validateItem(ItemInput input) {
    normalizedMode(input.mode());
    if (!ANSWER_TYPES.contains(input.answerType()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "答案类型不正确");
    if (jdbc.sql("SELECT COUNT(*) FROM cet_paper WHERE id=:id")
            .param("id", input.paperId())
            .query(Integer.class)
            .single()
        == 0) throw notFound();
  }

  private String normalizedLevel(String level) {
    String value = level == null ? "" : level.toUpperCase();
    if (!LEVELS.contains(value))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "考试级别不正确");
    return value;
  }

  private String normalizedMode(String mode) {
    String value = mode == null ? "" : mode.toUpperCase();
    if (!MODES.contains(value))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "练习模式不正确");
    return value;
  }

  private String options(String value) {
    return value == null || value.isBlank() ? "null" : value;
  }

  private String normalizedPaperAssetType(String type) {
    String value = type == null ? "" : type.toLowerCase();
    if (!PAPER_ASSET_TYPES.contains(value))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "套卷资料类型不正确");
    return value;
  }

  private PaperAssetFile findPaperAsset(long id, String type) {
    String prefix =
        switch (type) {
          case "question" -> "question";
          case "answer" -> "answer";
          default -> "audio";
        };
    return jdbc.sql(
            """
            SELECT %s_storage_name storage_name,%s_original_name original_name
            FROM cet_paper_asset WHERE paper_id=:id
            """
                .formatted(prefix, prefix))
        .param("id", id)
        .query(PaperAssetFile.class)
        .optional()
        .orElseThrow(this::notFound);
  }

  private boolean paperAssetExists(long id, String type) {
    try {
      PaperAssetFile asset = findPaperAsset(id, type);
      return asset.storageName() != null && Files.isRegularFile(safePath(asset.storageName()));
    } catch (ResponseStatusException ignored) {
      return false;
    }
  }

  private Path safePath(String storage) {
    Path path = uploadDir.resolve(storage).normalize();
    if (!path.startsWith(uploadDir))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径不安全");
    return path;
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "题库内容不存在");
  }

  public record CetPaper(
      long id,
      String level,
      int examYear,
      int examMonth,
      int setNumber,
      String title,
      boolean published,
      String sourceName,
      String sourcePageUrl,
      String usageNote,
      String questionOriginalName,
      String answerOriginalName,
      String audioOriginalName,
      boolean questionAvailable,
      boolean answerAvailable,
      boolean audioAvailable) {}

  public record CetItem(
      long id,
      long paperId,
      String level,
      int examYear,
      int examMonth,
      int setNumber,
      String paperTitle,
      String mode,
      String section,
      String title,
      String prompt,
      String passage,
      String translation,
      String analysis,
      String keySentence,
      String answerType,
      String optionsJson,
      String correctAnswer,
      int itemOrder,
      String audioOriginalName,
      Integer audioStartMs,
      Integer audioEndMs) {}

  public record PaperInput(
      @NotBlank String level,
      int examYear,
      int examMonth,
      int setNumber,
      @NotBlank @Size(max = 120) String title) {}

  public record ItemInput(
      @NotNull Long paperId,
      @NotBlank String mode,
      @NotBlank @Size(max = 32) String section,
      @NotBlank @Size(max = 160) String title,
      String prompt,
      String passage,
      String translation,
      String analysis,
      String keySentence,
      @NotBlank String answerType,
      String optionsJson,
      String correctAnswer,
      int itemOrder,
      Integer audioStartMs,
      Integer audioEndMs) {}

  private record AudioFile(
      String audioStorageName, String audioOriginalName, String audioMimeType) {}

  private record PaperAssetFile(String storageName, String originalName) {}

  private record PaperRow(
      long id,
      String level,
      int examYear,
      int examMonth,
      int setNumber,
      String title,
      boolean published,
      String sourceName,
      String sourcePageUrl,
      String usageNote,
      String questionOriginalName,
      String answerOriginalName,
      String audioOriginalName) {}
}
