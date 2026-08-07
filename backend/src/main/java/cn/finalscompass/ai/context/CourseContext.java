package cn.finalscompass.ai.context;

import java.util.List;

/** Bounded, verified business context supplied to a learning workflow. */
public record CourseContext(Long courseId, Long teacherId, String courseName,
                            List<Material> materials, String teacherProfile) {
    public CourseContext { materials = List.copyOf(materials); }
    public static CourseContext empty() { return new CourseContext(null, null, null, List.of(), null); }
    public boolean available() { return courseId != null; }

    public record Material(long id, String title, String type, String description) {}
}
