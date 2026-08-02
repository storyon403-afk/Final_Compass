package cn.finalscompass.controller;

import cn.finalscompass.model.ApiModels.Course;
import cn.finalscompass.model.ApiModels.CreateCourse;
import cn.finalscompass.model.ApiModels.CreateTeacher;
import cn.finalscompass.model.ApiModels.College;
import cn.finalscompass.model.ApiModels.CreateCollege;
import cn.finalscompass.model.ApiModels.Teacher;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CatalogController {
    private final JdbcClient jdbc;
    private final AuthService auth;
    public CatalogController(JdbcClient jdbc, AuthService auth) { this.jdbc = jdbc; this.auth = auth; }

    @GetMapping
    public List<Course> courses() {
        return jdbc.sql("""
                SELECT c.id, c.slug, c.name, c.code, c.category,
                       cp.college, cp.program_name, cp.course_type
                FROM course c
                JOIN course_program cp ON cp.course_id = c.id
                WHERE c.active = TRUE
                ORDER BY cp.college, cp.program_name, cp.course_type, c.name
                """)
                .query(Course.class).list();
    }

    @GetMapping("/colleges")
    public List<College> colleges() {
        return jdbc.sql("SELECT id, name FROM college WHERE active=TRUE ORDER BY created_at, name")
                .query(College.class).list();
    }

    @PostMapping("/colleges")
    @ResponseStatus(HttpStatus.CREATED)
    public College addCollege(HttpServletRequest servletRequest, @Valid @RequestBody CreateCollege request) {
        auth.requireAdmin(servletRequest);
        String name = request.name().trim();
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM college WHERE name=:name").param("name", name)
                .query(Integer.class).single() > 0;
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "学院已存在");
        jdbc.sql("INSERT INTO college(name) VALUES (:name)").param("name", name).update();
        long id = jdbc.sql("SELECT id FROM college WHERE name=:name").param("name", name).query(Long.class).single();
        return new College(id, name);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Course addCourse(HttpServletRequest servletRequest, @Valid @RequestBody CreateCourse request) {
        auth.requireAdmin(servletRequest);
        String name = request.name().trim();
        String code = request.code().trim().toUpperCase();
        String category = request.category().trim();
        String college = request.college().trim();
        String programName = request.programName() == null ? null : request.programName().trim();
        String courseType = request.courseType() == null ? null : request.courseType().trim();
        boolean collegeExists = jdbc.sql("SELECT COUNT(*) FROM college WHERE name=:name AND active=TRUE")
                .param("name", college).query(Integer.class).single() > 0;
        if (!collegeExists) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先添加所属学院");
        CourseIdentity existing = jdbc.sql("""
                SELECT id,slug,name,category FROM course
                WHERE code=:code AND name=:name AND active=TRUE
                ORDER BY id LIMIT 1
                """).param("code", code).param("name", name)
                .query(CourseIdentity.class).optional().orElse(null);
        if (existing == null) {
            CourseIdentity conflict = jdbc.sql("""
                    SELECT id,slug,name,category FROM course
                    WHERE code=:code AND active=TRUE ORDER BY id LIMIT 1
                    """).param("code", code).query(CourseIdentity.class).optional().orElse(null);
            if (conflict != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "课程代码 " + code + " 已属于“" + conflict.name() + "”，请检查课程名称");
            }
        }
        if (existing != null) {
            jdbc.sql("""
                    INSERT INTO course_program(course_id,college,program_name,course_type)
                    VALUES (:course,:college,:program,:type)
                    ON DUPLICATE KEY UPDATE course_type=VALUES(course_type)
                    """).param("course", existing.id()).param("college", college)
                    .param("program", programName).param("type", courseType).update();
            return new Course(existing.id(), existing.slug(), existing.name(), code, existing.category(),
                    college, programName, courseType);
        }
        String slug = "course-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.sql("INSERT INTO course(slug,name,code,category,college,program_name,course_type) VALUES (:slug,:name,:code,:category,:college,:program,:type)")
                .param("slug", slug).param("name", name).param("code", code)
                .param("category", category).param("college", college).param("program", programName)
                .param("type", courseType).update();
        long id = jdbc.sql("SELECT id FROM course WHERE slug=:slug").param("slug", slug).query(Long.class).single();
        jdbc.sql("INSERT INTO course_program(course_id,college,program_name,course_type) VALUES (:course,:college,:program,:type)")
                .param("course", id).param("college", college).param("program", programName)
                .param("type", courseType).update();
        return new Course(id, slug, name, code, category, college, programName, courseType);
    }

    @GetMapping("/{courseSlug}/teachers")
    public List<Teacher> teachers(@PathVariable String courseSlug) {
        return jdbc.sql("""
            SELECT t.id, t.slug, t.name, t.college,
              COUNT(DISTINCT r.id) resource_count, COUNT(DISTINCT d.id) post_count
            FROM teacher t
            JOIN teacher_course tc ON tc.teacher_id = t.id
            JOIN course c ON c.id = tc.course_id
            LEFT JOIN resource r ON r.teacher_id = t.id AND r.course_id = c.id AND r.status = 'PUBLISHED'
            LEFT JOIN discussion d ON d.teacher_id = t.id AND d.course_id = c.id AND d.status = 'VISIBLE'
            WHERE c.slug = :courseSlug GROUP BY t.id, t.slug, t.name, t.college ORDER BY t.name
            """).param("courseSlug", courseSlug).query(Teacher.class).list();
    }

    @PostMapping("/{courseSlug}/teachers")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Teacher addTeacher(HttpServletRequest servletRequest, @PathVariable String courseSlug, @Valid @RequestBody CreateTeacher request) {
        auth.requireAdmin(servletRequest);
        long courseId = jdbc.sql("SELECT id FROM course WHERE slug=:slug AND active=TRUE")
                .param("slug", courseSlug).query(Long.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "课程不存在"));
        String name = request.name().trim();
        String college = request.college().trim();
        TeacherIdentity identity = jdbc.sql("SELECT id, slug FROM teacher WHERE name=:name AND college=:college LIMIT 1")
                .param("name", name).param("college", college).query(TeacherIdentity.class).optional().orElse(null);
        if (identity == null) {
            String slug = "teacher-" + UUID.randomUUID().toString().substring(0, 8);
            jdbc.sql("INSERT INTO teacher(slug,name,college) VALUES (:slug,:name,:college)")
                    .param("slug", slug).param("name", name).param("college", college).update();
            long teacherId = jdbc.sql("SELECT id FROM teacher WHERE slug=:slug").param("slug", slug).query(Long.class).single();
            identity = new TeacherIdentity(teacherId, slug);
        }
        jdbc.sql("""
            INSERT IGNORE INTO teacher_course(teacher_id,course_id,term,review_note)
            VALUES (:teacher,:course,'当前学期','同学共建页面，具体考试范围以课堂通知为准。')
            """).param("teacher", identity.id()).param("course", courseId).update();
        return new Teacher(identity.id(), identity.slug(), name, college, 0, 0);
    }

    private record TeacherIdentity(long id, String slug) {}
    private record CourseIdentity(long id, String slug, String name, String category) {}
}
