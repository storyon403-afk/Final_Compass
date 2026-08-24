package cn.finalscompass.course.domain;
import cn.finalscompass.model.ApiModels.*;
import java.util.List;
/** 课程导航模块拥有的持久化端口 */
public interface CourseCatalogRepository {
  List<Course> findCourses();
  List<College> findColleges();
  College createCollege(CreateCollege request);
  List<Program> findPrograms();
  Program createProgram(CreateProgram request);
  Course createCourse(CreateCourse request);
  List<Teacher> findTeachers(String courseSlug);
  Teacher createTeacher(String courseSlug, CreateTeacher request);
}
