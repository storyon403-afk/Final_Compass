package cn.finalscompass.controller;

import cn.finalscompass.model.ApiModels.CircleSummary;
import cn.finalscompass.model.ApiModels.CreateDiscussion;
import cn.finalscompass.model.ApiModels.Discussion;
import cn.finalscompass.model.ApiModels.Resource;
import cn.finalscompass.model.ApiModels.StudyGuide;
import cn.finalscompass.model.ApiModels.UpdateStudyGuide;
import cn.finalscompass.model.ApiModels.CreateGuideSubmission;
import cn.finalscompass.model.ApiModels.GuideSubmission;
import cn.finalscompass.service.AnonymousIdentityService;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.ActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/circles/{courseSlug}/{teacherSlug}")
public class CircleController {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "zip", "png", "jpg", "jpeg");
    private final JdbcClient jdbc;
    private final AnonymousIdentityService identities;
    private final AuthService auth;
    private final ActivityService activity;
    private final Path uploadDir;

    public CircleController(JdbcClient jdbc, AnonymousIdentityService identities, AuthService auth, ActivityService activity,
                            @Value("${app.upload-dir}") String uploadDir) {
        this.jdbc = jdbc;
        this.identities = identities;
        this.auth = auth;
        this.activity = activity;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @GetMapping("/resources")
    public List<Resource> resources(@PathVariable String courseSlug, @PathVariable String teacherSlug) {
        return jdbc.sql("""
            SELECT r.id, r.title, r.resource_type type, r.description, r.original_name,
              r.file_size, r.download_count downloads, r.thanks_count thanks,
              u.nickname contributor, r.created_at
            FROM resource r JOIN anonymous_user u ON u.id = r.uploader_id
            JOIN course c ON c.id = r.course_id JOIN teacher t ON t.id = r.teacher_id
            WHERE c.slug = :course AND t.slug = :teacher AND r.status = 'PUBLISHED'
            ORDER BY r.created_at DESC
            """).param("course", courseSlug).param("teacher", teacherSlug).query(Resource.class).list();
    }

    @GetMapping("/resources/{resourceId}/file")
    public ResponseEntity<org.springframework.core.io.Resource> file(@PathVariable String courseSlug,
                                                                     @PathVariable String teacherSlug,
                                                                     @PathVariable long resourceId,
                                                                     @RequestParam(defaultValue = "inline") String disposition) {
        StoredFile stored = jdbc.sql("""
            SELECT r.storage_name, r.original_name, r.mime_type
            FROM resource r JOIN course c ON c.id=r.course_id JOIN teacher t ON t.id=r.teacher_id
            WHERE r.id=:id AND c.slug=:course AND t.slug=:teacher AND r.status='PUBLISHED'
            """).param("id", resourceId).param("course", courseSlug).param("teacher", teacherSlug)
                .query(StoredFile.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在或尚未公开"));
        Path path = uploadDir.resolve(stored.storageName()).normalize();
        if (!path.startsWith(uploadDir) || !Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料文件不存在");
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(stored.mimeType() == null ? "application/octet-stream" : stored.mimeType()); }
        catch (IllegalArgumentException ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        var contentDisposition = "attachment".equalsIgnoreCase(disposition)
                ? org.springframework.http.ContentDisposition.attachment()
                : org.springframework.http.ContentDisposition.inline();
        jdbc.sql("UPDATE resource SET download_count=download_count+1 WHERE id=:id").param("id", resourceId).update();
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.filename(stored.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(new org.springframework.core.io.FileSystemResource(path));
    }

    @PostMapping("/resources/{resourceId}/thanks")
    public Map<String, Object> thank(HttpServletRequest request, @PathVariable String courseSlug,
                                     @PathVariable String teacherSlug, @PathVariable long resourceId) {
        long accountId = auth.current(request).id();
        long userId = identities.internalIdForAccount(accountId);
        boolean published = jdbc.sql("""
            SELECT COUNT(*) FROM resource r JOIN course c ON c.id=r.course_id JOIN teacher t ON t.id=r.teacher_id
            WHERE r.id=:id AND c.slug=:course AND t.slug=:teacher AND r.status='PUBLISHED'
            """).param("id", resourceId).param("course", courseSlug).param("teacher", teacherSlug).query(Integer.class).single() == 1;
        if (!published) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在或尚未公开");
        int inserted = jdbc.sql("INSERT IGNORE INTO resource_thank(resource_id,anonymous_user_id) VALUES (:resource,:user)")
                .param("resource", resourceId).param("user", userId).update();
        if (inserted == 1) jdbc.sql("UPDATE resource SET thanks_count=thanks_count+1 WHERE id=:id").param("id", resourceId).update();
        int count = jdbc.sql("SELECT thanks_count FROM resource WHERE id=:id").param("id", resourceId).query(Integer.class).single();
        return Map.of("added", inserted == 1, "thanks", count);
    }

    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public void upload(HttpServletRequest request, @PathVariable String courseSlug, @PathVariable String teacherSlug,
                       @RequestParam @jakarta.validation.constraints.Size(max = 120) String title,
                       @RequestParam(defaultValue = "同学分享") String type, @RequestParam(defaultValue = "") String description,
                       @RequestPart MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空或超过 20MB");
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "resource" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持此文件类型");
        long accountId = auth.current(request).id();
        long userId = identities.internalIdForAccount(accountId);
        long courseId = lookupId("course", courseSlug);
        long teacherId = lookupId("teacher", teacherSlug);
        Files.createDirectories(uploadDir);
        String storageName = UUID.randomUUID() + "." + ext.toLowerCase();
        file.transferTo(uploadDir.resolve(storageName));
        jdbc.sql("""
            INSERT INTO resource(teacher_id, course_id, uploader_id, title, resource_type, description,
              original_name, storage_name, mime_type, file_size, status)
            VALUES (:teacher, :course, :user, :title, :type, :description, :original, :storage, :mime, :size, 'PENDING')
            """).param("teacher", teacherId).param("course", courseId).param("user", userId)
                .param("title", title).param("type", type).param("description", description)
                .param("original", original).param("storage", storageName).param("mime", file.getContentType())
                .param("size", file.getSize()).update();
        activity.recordResourceSubmitted(accountId, storageName);
    }

    @GetMapping("/discussions")
    public List<Discussion> discussions(@PathVariable String courseSlug, @PathVariable String teacherSlug,
                                        @RequestParam(required = false) LocalDate date) {
        String sql = """
            SELECT d.id, u.nickname author, d.content, d.like_count likes,
              (SELECT COUNT(*) FROM discussion child WHERE child.parent_id = d.id AND child.status = 'VISIBLE') replies,
              d.parent_id, d.created_at
            FROM discussion d JOIN anonymous_user u ON u.id = d.author_id
            JOIN course c ON c.id = d.course_id JOIN teacher t ON t.id = d.teacher_id
            WHERE c.slug = :course AND t.slug = :teacher AND d.status = 'VISIBLE' AND d.parent_id IS NULL
            %s
            ORDER BY d.created_at DESC
            """.formatted(date == null ? "" : "AND DATE(d.created_at) = :date");
        var query = jdbc.sql(sql).param("course", courseSlug).param("teacher", teacherSlug);
        if (date != null) query = query.param("date", date);
        return query.query(Discussion.class).list();
    }

    @PostMapping("/discussions")
    @ResponseStatus(HttpStatus.CREATED)
    public Discussion discuss(HttpServletRequest servletRequest, @PathVariable String courseSlug,
                              @PathVariable String teacherSlug, @Valid @RequestBody CreateDiscussion request) {
        long userId = identities.internalIdForAccount(auth.current(servletRequest).id());
        long courseId = lookupId("course", courseSlug);
        long teacherId = lookupId("teacher", teacherSlug);
        jdbc.sql("INSERT INTO discussion(teacher_id, course_id, author_id, parent_id, content, status) VALUES (:teacher,:course,:user,:parent,:content,'PENDING')")
                .param("teacher", teacherId).param("course", courseId).param("user", userId)
                .param("parent", request.parentId()).param("content", request.content()).update();
        String nickname = jdbc.sql("SELECT nickname FROM anonymous_user WHERE id=:id").param("id", userId).query(String.class).single();
        return new Discussion(0, nickname, request.content(), 0, 0, request.parentId(), LocalDateTime.now());
    }

    @GetMapping("/summary")
    public CircleSummary summary(@PathVariable String courseSlug, @PathVariable String teacherSlug) {
        return jdbc.sql("""
            SELECT COUNT(DISTINCT r.id) resources, COUNT(DISTINCT d.id) discussions,
              COUNT(DISTINCT COALESCE(r.uploader_id, d.author_id)) contributors
            FROM course c JOIN teacher_course tc ON tc.course_id=c.id JOIN teacher t ON t.id=tc.teacher_id
            LEFT JOIN resource r ON r.course_id=c.id AND r.teacher_id=t.id AND r.status='PUBLISHED'
            LEFT JOIN discussion d ON d.course_id=c.id AND d.teacher_id=t.id AND d.status='VISIBLE'
            WHERE c.slug=:course AND t.slug=:teacher
            """).param("course", courseSlug).param("teacher", teacherSlug).query(CircleSummary.class).single();
    }

    @GetMapping("/guide")
    public StudyGuide guide(@PathVariable String courseSlug, @PathVariable String teacherSlug) {
        return jdbc.sql("""
            SELECT g.content_markdown, g.change_note, g.updated_at
            FROM study_guide g JOIN course c ON c.id=g.course_id JOIN teacher t ON t.id=g.teacher_id
            WHERE c.slug=:course AND t.slug=:teacher
            """).param("course", courseSlug).param("teacher", teacherSlug).query(StudyGuide.class).optional()
                .orElse(new StudyGuide("", "", null));
    }

    @PutMapping("/guide")
    @Transactional
    public StudyGuide updateGuide(HttpServletRequest servletRequest, @PathVariable String courseSlug,
                                  @PathVariable String teacherSlug, @Valid @RequestBody UpdateStudyGuide request) {
        var admin = auth.requireAdmin(servletRequest);
        long courseId = lookupId("course", courseSlug);
        long teacherId = lookupId("teacher", teacherSlug);
        boolean teachesCourse = jdbc.sql("SELECT COUNT(*) FROM teacher_course WHERE course_id=:course AND teacher_id=:teacher")
                .param("course", courseId).param("teacher", teacherId).query(Integer.class).single() > 0;
        if (!teachesCourse) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "老师不属于该课程");
        String content = request.contentMarkdown() == null ? "" : request.contentMarkdown().trim();
        String changeNote = request.changeNote() == null ? "" : request.changeNote().trim();
        jdbc.sql("""
            INSERT INTO study_guide(course_id,teacher_id,content_markdown,change_note,updated_by)
            VALUES (:course,:teacher,:content,:note,:editor)
            ON DUPLICATE KEY UPDATE content_markdown=:content,change_note=:note,updated_by=:editor,updated_at=NOW()
            """).param("course", courseId).param("teacher", teacherId).param("content", content).param("note", changeNote)
                .param("editor", admin.id()).update();
        if (request.incorporatedSubmissionIds() != null && !request.incorporatedSubmissionIds().isEmpty()) {
            jdbc.sql("""
                UPDATE guide_submission SET status='INCORPORATED',incorporated_at=NOW()
                WHERE id IN (:ids) AND course_id=:course AND teacher_id=:teacher AND status='APPROVED'
                """).param("ids", request.incorporatedSubmissionIds()).param("course", courseId)
                    .param("teacher", teacherId).update();
        }
        return guide(courseSlug, teacherSlug);
    }

    @PostMapping("/guide/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> submitGuideReference(HttpServletRequest servletRequest, @PathVariable String courseSlug,
                                                    @PathVariable String teacherSlug,
                                                    @Valid @RequestBody CreateGuideSubmission request) {
        long authorId = identities.internalIdForAccount(auth.current(servletRequest).id());
        long courseId = lookupId("course", courseSlug);
        long teacherId = lookupId("teacher", teacherSlug);
        boolean teachesCourse = jdbc.sql("SELECT COUNT(*) FROM teacher_course WHERE course_id=:course AND teacher_id=:teacher")
                .param("course", courseId).param("teacher", teacherId).query(Integer.class).single() > 0;
        if (!teachesCourse) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "老师不属于该课程");
        jdbc.sql("""
            INSERT INTO guide_submission(course_id,teacher_id,author_id,content_markdown,status)
            VALUES (:course,:teacher,:author,:content,'PENDING')
            """).param("course", courseId).param("teacher", teacherId).param("author", authorId)
                .param("content", request.contentMarkdown().trim()).update();
        return Map.of("status", "PENDING");
    }

    @GetMapping("/guide/submissions")
    public List<GuideSubmission> approvedGuideReferences(HttpServletRequest servletRequest,
                                                          @PathVariable String courseSlug,
                                                          @PathVariable String teacherSlug) {
        auth.requireAdmin(servletRequest);
        return jdbc.sql("""
            SELECT s.id,s.content_markdown,u.nickname author,s.status,s.created_at
            FROM guide_submission s JOIN anonymous_user u ON u.id=s.author_id
            JOIN course c ON c.id=s.course_id JOIN teacher t ON t.id=s.teacher_id
            WHERE c.slug=:course AND t.slug=:teacher AND s.status='APPROVED'
            ORDER BY s.created_at
            """).param("course", courseSlug).param("teacher", teacherSlug).query(GuideSubmission.class).list();
    }

    private long lookupId(String table, String slug) {
        if (!table.equals("course") && !table.equals("teacher")) throw new IllegalArgumentException("unsupported lookup");
        return jdbc.sql("SELECT id FROM " + table + " WHERE slug = :slug").param("slug", slug).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "课程或老师不存在"));
    }

    private record StoredFile(String storageName, String originalName, String mimeType) {}
}
