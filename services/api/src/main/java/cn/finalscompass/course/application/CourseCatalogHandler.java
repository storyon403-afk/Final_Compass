package cn.finalscompass.course.application;
import cn.finalscompass.course.domain.CourseCatalogRepository;
import cn.finalscompass.model.ApiModels.*;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 课程导航请求的应用用例边界 */
@Service
public class CourseCatalogHandler {
  private final CourseCatalogRepository repository;
  private final AuthorizationPolicy authorization;
  public CourseCatalogHandler(CourseCatalogRepository repository, AuthorizationPolicy authorization) {
    this.repository = repository; this.authorization = authorization;
  }
  public List<Course> courses() { return repository.findCourses(); }
  public List<College> colleges() { return repository.findColleges(); }
  public List<Program> programs() { return repository.findPrograms(); }
  public List<Teacher> teachers(String slug) { return repository.findTeachers(slug); }
  @Transactional public College createCollege(AuthService.CurrentUser user, CreateCollege c) { authorization.requireAdmin(user); return repository.createCollege(c); }
  @Transactional public Program createProgram(AuthService.CurrentUser user, CreateProgram c) { authorization.requireAdmin(user); return repository.createProgram(c); }
  @Transactional public Course createCourse(AuthService.CurrentUser user, CreateCourse c) { authorization.requireAdmin(user); return repository.createCourse(c); }
  @Transactional public Teacher createTeacher(AuthService.CurrentUser user, String slug, CreateTeacher c) { authorization.requireAdmin(user); return repository.createTeacher(slug, c); }
}
