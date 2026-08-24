package cn.finalscompass.controller;

import cn.finalscompass.course.application.CourseCatalogHandler;
import cn.finalscompass.model.ApiModels.*;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.Authenticated;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 课程导航请求的 HTTP 适配器 */
@RestController
@RequestMapping("/api/courses")
public class CatalogController {
  private final CourseCatalogHandler handler;
  public CatalogController(CourseCatalogHandler handler) { this.handler = handler; }

  @GetMapping public List<Course> courses() { return handler.courses(); }
  @GetMapping("/colleges") public List<College> colleges() { return handler.colleges(); }
  @GetMapping("/programs") public List<Program> programs() { return handler.programs(); }
  @GetMapping("/{courseSlug}/teachers")
  public List<Teacher> teachers(@PathVariable String courseSlug) { return handler.teachers(courseSlug); }

  @PostMapping("/colleges") @ResponseStatus(HttpStatus.CREATED)
  public College addCollege(@Authenticated AuthService.CurrentUser user,@Valid @RequestBody CreateCollege body) {
    return handler.createCollege(user,body);
  }
  @PostMapping("/programs") @ResponseStatus(HttpStatus.CREATED)
  public Program addProgram(@Authenticated AuthService.CurrentUser user,@Valid @RequestBody CreateProgram body) {
    return handler.createProgram(user,body);
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Course addCourse(@Authenticated AuthService.CurrentUser user,@Valid @RequestBody CreateCourse body) {
    return handler.createCourse(user,body);
  }
  @PostMapping("/{courseSlug}/teachers") @ResponseStatus(HttpStatus.CREATED)
  public Teacher addTeacher(@Authenticated AuthService.CurrentUser user,@PathVariable String courseSlug,
      @Valid @RequestBody CreateTeacher body) {
    return handler.createTeacher(user,courseSlug,body);
  }
}
