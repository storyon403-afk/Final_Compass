package cn.finalscompass.controller;

import cn.finalscompass.circle.application.CircleQueryHandler;
import cn.finalscompass.circle.application.CircleCommandHandler;
import cn.finalscompass.model.ApiModels.CircleSummary;
import cn.finalscompass.model.ApiModels.CreateDiscussion;
import cn.finalscompass.model.ApiModels.CreateGuideSubmission;
import cn.finalscompass.model.ApiModels.Discussion;
import cn.finalscompass.model.ApiModels.GuideSubmission;
import cn.finalscompass.model.ApiModels.Resource;
import cn.finalscompass.model.ApiModels.StudyGuide;
import cn.finalscompass.model.ApiModels.UpdateStudyGuide;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.Authenticated;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供指定课程与教师圈子的资源、讨论和攻略功能 * 普通用户可贡献内容，攻略维护与投稿审核仅向管理员开放 */
@RestController
@RequestMapping("/api/circles/{courseSlug}/{teacherSlug}")
public class CircleController {
  private final CircleQueryHandler queries;
  private final CircleCommandHandler commands;

  public CircleController(
      CircleQueryHandler queries,
      CircleCommandHandler commands) {
    this.queries = queries;
    this.commands = commands;
  }

  @GetMapping("/resources")
  public List<Resource> resources(
      @PathVariable String courseSlug, @PathVariable String teacherSlug) {
    return queries.resources(courseSlug, teacherSlug);
  }

  @GetMapping("/resources/{resourceId}/file")
  public ResponseEntity<org.springframework.core.io.Resource> file(
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug,
      @PathVariable long resourceId,
      @RequestParam(defaultValue = "inline") String disposition) {
    return queries.file(courseSlug, teacherSlug, resourceId, disposition);
  }

  @PostMapping("/resources/{resourceId}/thanks")
  public Map<String, Object> thank(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug,
      @PathVariable long resourceId) {
    return commands.thank(user, courseSlug, teacherSlug, resourceId);
  }

  @PostMapping("/resources")
  @ResponseStatus(HttpStatus.CREATED)
  public void upload(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug,
      @RequestParam @jakarta.validation.constraints.Size(max = 120) String title,
      @RequestParam(defaultValue = "同学分享") String type,
      @RequestParam(defaultValue = "") String description,
      @RequestPart MultipartFile file)
      throws IOException {
    commands.upload(user, courseSlug, teacherSlug, title, type, description, file);
  }

  @GetMapping("/discussions")
  public List<Discussion> discussions(
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug,
      @RequestParam(required = false) LocalDate date) {
    return queries.discussions(courseSlug, teacherSlug, date);
  }

  @PostMapping("/discussions")
  @ResponseStatus(HttpStatus.CREATED)
  public Discussion discuss(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug,
      @Valid @RequestBody CreateDiscussion request) {
    return commands.discuss(user, courseSlug, teacherSlug, request);
  }

  @GetMapping("/summary")
  public CircleSummary summary(@PathVariable String courseSlug, @PathVariable String teacherSlug) {
    return queries.summary(courseSlug, teacherSlug);
  }

  @GetMapping("/guide")
  public StudyGuide guide(@PathVariable String courseSlug, @PathVariable String teacherSlug) {
    return queries.guide(courseSlug, teacherSlug);
  }

  @PutMapping("/guide")
  public StudyGuide updateGuide(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug,
      @Valid @RequestBody UpdateStudyGuide request) {
    return commands.updateGuide(user, courseSlug, teacherSlug, request);
  }

  @PostMapping("/guide/submissions")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> submitGuideReference(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug,
      @Valid @RequestBody CreateGuideSubmission request) {
    return commands.submitGuide(user, courseSlug, teacherSlug, request);
  }

  @GetMapping("/guide/submissions")
  public List<GuideSubmission> approvedGuideReferences(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable String courseSlug,
      @PathVariable String teacherSlug) {
    return queries.approved(user, courseSlug, teacherSlug);
  }

}
