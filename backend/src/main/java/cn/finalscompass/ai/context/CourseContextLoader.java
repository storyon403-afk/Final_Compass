package cn.finalscompass.ai.context;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/** Loads only published, size-bounded course data from the business database. */
@Component
public class CourseContextLoader {
    private final JdbcClient jdbc;
    public CourseContextLoader(JdbcClient jdbc) { this.jdbc = jdbc; }

    public CourseContext load(Long courseId, Long teacherId) {
        if (courseId == null) return CourseContext.empty();
        String courseName = jdbc.sql("SELECT name FROM course WHERE id=:id AND active=TRUE")
                .param("id", courseId).query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("课程不存在或已停用"));
        String teacherProfile = null;
        if (teacherId != null) {
            teacherProfile = jdbc.sql("""
                    SELECT CONCAT(t.name,'；',COALESCE(MAX(tc.review_note),'暂无教师教学备注'))
                    FROM teacher t JOIN teacher_course tc ON tc.teacher_id=t.id
                    WHERE t.id=:teacher AND tc.course_id=:course GROUP BY t.id,t.name
                    """).param("teacher", teacherId).param("course", courseId).query(String.class).optional()
                    .orElseThrow(() -> new IllegalArgumentException("该教师与课程不匹配"));
        }
        String materialSql = """
                SELECT id,title,resource_type,LEFT(COALESCE(description,''),500) description
                FROM resource WHERE course_id=:course AND status='PUBLISHED'
                """ + (teacherId == null ? "" : " AND teacher_id=:teacher") + " ORDER BY created_at DESC LIMIT 20";
        var statement = jdbc.sql(materialSql).param("course", courseId);
        if (teacherId != null) statement = statement.param("teacher", teacherId);
        List<CourseContext.Material> materials = statement.query((rs, row) -> new CourseContext.Material(
                rs.getLong("id"), rs.getString("title"), rs.getString("resource_type"), rs.getString("description"))).list();
        return new CourseContext(courseId, teacherId, courseName, materials, teacherProfile);
    }
}
