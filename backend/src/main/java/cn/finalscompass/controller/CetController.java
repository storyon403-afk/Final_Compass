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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供 CET 试卷、题目和音频资料访问，以及受管理员权限保护的内容维护接口。
 * 上传文件始终限制在配置的上传目录内，避免客户端输入造成路径越界。
 */
@RestController
@RequestMapping("/api/cet")
public class CetController {
  private static final Set<String> LEVELS = Set.of("CET4", "CET6");
  private static final Set<String> MODES = Set.of("PRACTICE", "INTENSIVE");
  private static final Set<String> ANSWER_TYPES = Set.of("CHOICE", "TEXT");
  private static final Set<String> AUDIO_EXTENSIONS =
      Set.of("mp3", "m4a", "wav", "ogg", "webm", "aac");
  private static final Set<String> PRACTICE_SECTIONS =
      Set.of("WRITING", "LISTENING_PASSAGE", "WORD_BANK", "MATCHING", "CAREFUL_READING", "TRANSLATION");
  private static final Set<String> INTENSIVE_SECTIONS =
      Set.of("NEWS", "LONG_CONVERSATION", "LISTENING_PASSAGE", "LECTURE");

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
              pa.original_name audio_original_name
            FROM cet_paper p LEFT JOIN cet_paper_asset a ON a.paper_id=p.id
            LEFT JOIN cet_practice_audio pa ON pa.paper_id=p.id
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
                    row.usageNote(), row.audioOriginalName(), practiceAudioExists(row.id())))
        .toList();
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
    THEN COALESCE(i.audio_original_name,pa.original_name)
    ELSE NULL END audio_original_name,
  i.audio_start_ms,i.audio_end_ms
FROM cet_item i JOIN cet_paper p ON p.id=i.paper_id
LEFT JOIN cet_practice_audio pa ON pa.paper_id=p.id
WHERE p.published=TRUE AND p.level=:level AND i.mode=:mode %s
ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number,i.item_order,i.id
"""
                    .formatted(sectionFilter))
            .param("level", normalizedLevel(level))
            .param("mode", normalizedMode(mode));
    if (!sectionFilter.isEmpty()) query = query.param("section", section.trim());
    return query.query(CetItem.class).list();
  }

  @GetMapping("/admin/items")
  public List<CetItem> adminItems(HttpServletRequest servletRequest) {
    auth.requireAdmin(servletRequest);
    return jdbc.sql(
            """
            SELECT i.id,i.paper_id,p.level,p.exam_year,p.exam_month,p.set_number,p.title paper_title,
              i.mode,i.section,i.title,i.prompt,i.passage,i.translation,i.analysis,i.key_sentence,
              i.answer_type,i.options_json,i.correct_answer,i.item_order,
              CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
                THEN COALESCE(i.audio_original_name,pa.original_name) ELSE NULL END audio_original_name,
              i.audio_start_ms,i.audio_end_ms
            FROM cet_item i JOIN cet_paper p ON p.id=i.paper_id
            LEFT JOIN cet_practice_audio pa ON pa.paper_id=p.id
            ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number,i.mode,i.section,i.item_order,i.id
            """)
        .query(CetItem.class).list();
  }

  @GetMapping("/admin/sections")
  public List<SectionResource> adminSections(HttpServletRequest servletRequest) {
    auth.requireAdmin(servletRequest);
    return jdbc.sql(
            """
            SELECT s.id,s.paper_id,p.title paper_title,p.level,s.mode,s.section,COUNT(i.id) item_count
            FROM cet_paper_section s JOIN cet_paper p ON p.id=s.paper_id
            LEFT JOIN cet_item i ON i.paper_id=s.paper_id AND i.mode=s.mode AND i.section=s.section
            GROUP BY s.id,s.paper_id,p.title,p.level,s.mode,s.section
            ORDER BY p.exam_year DESC,p.exam_month DESC,p.set_number,s.mode,s.section
            """)
        .query(SectionResource.class).list();
  }

  @DeleteMapping("/admin/sections/{id}/content")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearSection(HttpServletRequest servletRequest, @PathVariable long id) {
    auth.requireAdmin(servletRequest);
    SectionKey key = jdbc.sql("SELECT paper_id,mode,section FROM cet_paper_section WHERE id=:id")
        .param("id", id).query(SectionKey.class).optional().orElseThrow(this::notFound);
    List<String> files = jdbc.sql(
            "SELECT audio_storage_name FROM cet_item WHERE paper_id=:paper AND mode=:mode AND section=:section")
        .param("paper", key.paperId()).param("mode", key.mode()).param("section", key.section())
        .query(String.class).list();
    jdbc.sql("DELETE FROM cet_item WHERE paper_id=:paper AND mode=:mode AND section=:section")
        .param("paper", key.paperId()).param("mode", key.mode()).param("section", key.section()).update();
    files.stream().filter(value -> value != null && !value.isBlank()).distinct().forEach(this::deleteStoredFile);
  }

  @PostMapping("/papers")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
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
    long id = jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
    savePaperSource(id, input);
    createSectionSlots(id, normalizedLevel(input.level()));
    return Map.of("id", id);
  }

  @PutMapping("/papers/{id}")
  public void updatePaper(
      HttpServletRequest servletRequest,
      @PathVariable long id,
      @Valid @RequestBody PaperInput input) {
    auth.requireAdmin(servletRequest);
    String currentLevel = jdbc.sql("SELECT level FROM cet_paper WHERE id=:id")
        .param("id", id).query(String.class).optional().orElseThrow(this::notFound);
    if (!currentLevel.equals(normalizedLevel(input.level())))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有套卷的考试级别不可修改");
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
    savePaperSource(id, input);
  }

  @DeleteMapping("/papers/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePaper(HttpServletRequest servletRequest, @PathVariable long id) {
    auth.requireAdmin(servletRequest);
    List<String> files =
        jdbc.sql(
                """
                SELECT question_storage_name FROM cet_paper_asset WHERE paper_id=:id
                UNION ALL SELECT answer_storage_name FROM cet_paper_asset WHERE paper_id=:id
                UNION ALL SELECT storage_name FROM cet_practice_audio WHERE paper_id=:id
                UNION ALL SELECT audio_storage_name FROM cet_item WHERE paper_id=:id
                """)
            .param("id", id)
            .query(String.class)
            .list();
    if (jdbc.sql("DELETE FROM cet_paper WHERE id=:id").param("id", id).update() == 0)
      throw notFound();
    files.stream().filter(value -> value != null && !value.isBlank()).distinct().forEach(this::deleteStoredFile);
  }

  /** 为分类练习和精听精讲绑定整套共用音频；不属于完整套卷下载资料。 */
  @PostMapping("/papers/{id}/practice-audio")
  public void uploadPracticeAudio(
      HttpServletRequest servletRequest, @PathVariable long id, @RequestPart MultipartFile file)
      throws IOException {
    auth.requireAdmin(servletRequest);
    validateAudio(file);
    if (jdbc.sql("SELECT COUNT(*) FROM cet_paper WHERE id=:id")
            .param("id", id).query(Integer.class).single() == 0) throw notFound();
    String original = cleanOriginalName(file);
    String ext = StringUtils.getFilenameExtension(original).toLowerCase();
    Files.createDirectories(uploadDir);
    String storage = "cet-paper-" + UUID.randomUUID() + "." + ext;
    file.transferTo(safePath(storage));
    String previous = jdbc.sql("SELECT storage_name FROM cet_practice_audio WHERE paper_id=:id")
        .param("id", id).query(String.class).optional().orElse(null);
    jdbc.sql(
            """
            INSERT INTO cet_practice_audio(paper_id,storage_name,original_name,mime_type)
            VALUES (:id,:storage,:original,:mime)
            ON DUPLICATE KEY UPDATE storage_name=:storage,original_name=:original,mime_type=:mime
            """)
        .param("id", id).param("storage", storage).param("original", original)
        .param("mime", file.getContentType()).update();
    if (previous != null && !previous.equals(storage)) deleteStoredFile(previous);
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
    SectionKey original = jdbc.sql("SELECT paper_id,mode,section FROM cet_item WHERE id=:id")
        .param("id", id).query(SectionKey.class).optional().orElseThrow(this::notFound);
    if (original.paperId() != input.paperId() || !original.mode().equals(input.mode())
        || !original.section().equals(input.section()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有题目的套卷、训练方式和题型不可修改");
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

  @PostMapping("/items/{id}/audio")
  public void uploadAudio(
      HttpServletRequest servletRequest, @PathVariable long id, @RequestPart MultipartFile file)
      throws IOException {
    auth.requireAdmin(servletRequest);
    validateAudio(file);
    String original = cleanOriginalName(file);
    String ext = StringUtils.getFilenameExtension(original);
    if (jdbc.sql("SELECT COUNT(*) FROM cet_item WHERE id=:id")
            .param("id", id)
            .query(Integer.class)
            .single()
        == 0) throw notFound();
    Files.createDirectories(uploadDir);
    String storage = "cet-" + UUID.randomUUID() + "." + ext.toLowerCase();
    file.transferTo(safePath(storage));
    String previous = jdbc.sql("SELECT audio_storage_name FROM cet_item WHERE id=:id")
        .param("id", id).query(String.class).optional().orElse(null);
    jdbc.sql(
            """
UPDATE cet_item SET audio_storage_name=:storage,audio_original_name=:original,audio_mime_type=:mime WHERE id=:id
""")
        .param("storage", storage)
        .param("original", original)
        .param("mime", file.getContentType())
        .param("id", id)
        .update();
    if (previous != null && !previous.equals(storage)) deleteStoredFile(previous);
  }

  @GetMapping("/items/{id}/audio")
  public ResponseEntity<org.springframework.core.io.Resource> audio(@PathVariable long id) {
    AudioFile audio =
        jdbc.sql(
                """
SELECT CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
    THEN COALESCE(i.audio_storage_name,pa.storage_name) ELSE NULL END audio_storage_name,
  CASE WHEN i.mode='INTENSIVE' OR i.section='LISTENING_PASSAGE'
    THEN COALESCE(i.audio_original_name,pa.original_name) ELSE NULL END audio_original_name,
  COALESCE(i.audio_mime_type,pa.mime_type) audio_mime_type
FROM cet_item i JOIN cet_paper p ON p.id=i.paper_id
LEFT JOIN cet_practice_audio pa ON pa.paper_id=p.id
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
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            org.springframework.http.ContentDisposition.inline()
                .filename(audio.audioOriginalName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(new org.springframework.core.io.FileSystemResource(path));
  }

  private void validateItem(ItemInput input) {
    String mode = normalizedMode(input.mode());
    String section = input.section().trim();
    Set<String> allowed = mode.equals("PRACTICE") ? PRACTICE_SECTIONS : INTENSIVE_SECTIONS;
    if (!allowed.contains(section))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "题型与训练方式不匹配");
    if (!ANSWER_TYPES.contains(input.answerType()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "答案类型不正确");
    String paperLevel = jdbc.sql("SELECT level FROM cet_paper WHERE id=:id")
            .param("id", input.paperId())
            .query(String.class).optional().orElseThrow(this::notFound);
    if ((section.equals("NEWS") && !paperLevel.equals("CET4"))
        || (section.equals("LECTURE") && !paperLevel.equals("CET6")))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该题型不适用于所选考试级别");
    if (jdbc.sql("SELECT COUNT(*) FROM cet_paper_section WHERE paper_id=:paper AND mode=:mode AND section=:section")
            .param("paper", input.paperId()).param("mode", mode).param("section", section)
            .query(Integer.class).single() == 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该套卷不存在对应的题型资源槽位");
    if (input.audioStartMs() != null && input.audioEndMs() != null
        && input.audioEndMs() <= input.audioStartMs())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频终点必须晚于起点");
  }

  private void savePaperSource(long id, PaperInput input) {
    jdbc.sql(
            """
            INSERT INTO cet_paper_asset(paper_id,source_name,source_page_url,usage_note)
            VALUES (:id,:name,:url,:note)
            ON DUPLICATE KEY UPDATE source_name=:name,source_page_url=:url,usage_note=:note
            """)
        .param("id", id)
        .param("name", input.sourceName().trim())
        .param("url", input.sourcePageUrl().trim())
        .param("note", input.usageNote() == null ? "" : input.usageNote().trim())
        .update();
  }

  private void createSectionSlots(long paperId, String level) {
    for (String section : PRACTICE_SECTIONS) insertSectionSlot(paperId, "PRACTICE", section);
    insertSectionSlot(paperId, "INTENSIVE", "LONG_CONVERSATION");
    insertSectionSlot(paperId, "INTENSIVE", "LISTENING_PASSAGE");
    insertSectionSlot(paperId, "INTENSIVE", level.equals("CET4") ? "NEWS" : "LECTURE");
  }

  private void insertSectionSlot(long paperId, String mode, String section) {
    jdbc.sql("INSERT IGNORE INTO cet_paper_section(paper_id,mode,section) VALUES (:paper,:mode,:section)")
        .param("paper", paperId).param("mode", mode).param("section", section).update();
  }

  private void validateAudio(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 200L * 1024 * 1024)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频为空或超过 200MB");
    String original = cleanOriginalName(file);
    String ext = StringUtils.getFilenameExtension(original);
    if (ext == null || !AUDIO_EXTENSIONS.contains(ext.toLowerCase()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 MP3、M4A、WAV、OGG、WebM 或 AAC");
  }

  private String cleanOriginalName(MultipartFile file) {
    return StringUtils.cleanPath(file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename());
  }

  private void deleteStoredFile(String storage) {
    try { Files.deleteIfExists(safePath(storage)); } catch (IOException ignored) { }
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

  private boolean practiceAudioExists(long id) {
    String storage = jdbc.sql("SELECT storage_name FROM cet_practice_audio WHERE paper_id=:id")
        .param("id", id).query(String.class).optional().orElse(null);
    return storage != null && Files.isRegularFile(safePath(storage));
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
      String audioOriginalName,
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
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 120) String sourceName,
      @NotBlank @Size(max = 500) String sourcePageUrl,
      @Size(max = 500) String usageNote) {}

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
      String audioOriginalName) {}

  public record SectionResource(long id, long paperId, String paperTitle, String level,
      String mode, String section, long itemCount) {}

  private record SectionKey(long paperId, String mode, String section) {}
}
