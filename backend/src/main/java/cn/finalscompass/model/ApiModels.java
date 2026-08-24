package cn.finalscompass.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ApiMolels 是一个数据模型容器，集中存放了Finals Compass后端跨Controller使用的不可变API数据传输对象（DTO）：请求和响应数据结构
 * record组件名会直接映射JSON字段，参数上的Jakarta Validation注解进入业逻辑前会被Spring Boot自动验证，确保请求数据的完整性和合法性
 * 该类被设计为不可实例化的工具类（工具类一般没有对象行为，不应该被扩展），所有内部record都是静态的，便于在不同Controller之间共享和使用。通过这种方式，Final
 * Compass后端实现了数据传输对象的统一管理和验证
 */
public final class ApiModels {
  /** 私有构造函数，防止实例化 */
  private ApiModels() {}

  /** 登陆账号对外显示匿名身份 */
  public record AnonymousProfile(String publicId, String nickname) {}

  /** 登陆请求。密码只在当前请求内用于BCrypt校验 */
  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  /** 封闭内测资格申请 */
  public record BetaAccessRequest(
      /** 验证码成功后返回给前端的挑战上下文 */
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Email @Size(max = 254) String confirmEmail,
      @NotBlank
          @Pattern(regexp = "^\\+86(1[3-9]\\d{9})$", message = "手机号须为 +86 格式，例如 +8613812345678")
          String phone) {}

  public record BetaAccessChallenge(long requestId, String email, LocalDateTime expiresAt) {}

  /** 用户提交的一次验证码校验 */
  public record BetaAccessVerification(
      long requestId,
      /** 登录成功响应，包含会话令牌和前端权限判断所需的账号信息 */
      @NotBlank @Email String email,
      @NotBlank @Pattern(regexp = "^\\d{6}$", message = "请输入 6 位数字验证码") String code) {}

  public record AuthProfile(
      String token, String username, String displayName, String role, boolean mustChangePassword) {}

  /** 修改密码请求 */
  public record ChangePasswordRequest(
      @NotBlank String currentPassword, @NotBlank @Size(min = 6, max = 72) String newPassword) {}

  /** 当前系统公告 */
  public record Announcement(String content, boolean enabled, LocalDateTime updatedAt) {}

  /** 管理员修改公告的请求 */
  public record UpdateAnnouncement(@NotBlank @Size(max = 1000) String content, boolean enabled) {}

  /** 学院导航项 */
  public record College(long id, String name) {}

  /** 新建学院请求 */
  public record CreateCollege(@NotBlank @Size(max = 100) String name) {}

  /** 学院下的专业导航项 */
  public record Program(long id, String name, String college) {}

  /** 新建专业请求 */
  public record CreateProgram(
      @NotBlank @Size(max = 100) String college,
      @NotBlank @Size(max = 80) String name) {}

  /** 一门课程及其当前专业关联 */
  public record Course(
      long id,
      String slug,
      String name,
      String code,
      String category,
      String college,
      String programName,
      String courseType) {}

  /** 新建课程或把已有课程关联到另一专业的请求 */
  public record CreateCourse(
      /** 任课老师列表项及老师圈聚合数量 */
      @NotBlank @Size(max = 80) String name,
      @NotBlank @Size(max = 32) String code,
      @NotBlank @Size(max = 40) String category,
      @NotBlank @Size(max = 100) String college,
      @Size(max = 80) String programName,
      @Pattern(regexp = "专业课|非专业课", message = "课程类型只能是专业课或非专业课") String courseType) {}

  public record Teacher(
      long id, String slug, String name, String college, long resourceCount, long postCount) {}

  /** 新建或关联老师请求 */
  public record CreateTeacher(
      /** 已发布复习资料的前端展示模型 */
      @NotBlank @Size(max = 40) String name, @NotBlank @Size(max = 100) String college) {}

  public record Resource(
      long id,
      String title,
      String type,
      String description,
      String originalName,
      long fileSize,
      int downloads,
      int thanks,
      String contributor,
      LocalDateTime createdAt) {}

  /** 老师圈公开讨论及回复关系 */
  public record Discussion(
      long id,
      String author,
      String content,
      int likes,
      long replies,
      Long parentId,
      LocalDateTime createdAt) {}

  /** 新建讨论或回复请求；parentId 为空表示顶层讨论 */
  public record CreateDiscussion(@NotBlank @Size(max = 500) String content, Long parentId) {}

  /** 老师圈首页统计摘要 */
  public record CircleSummary(long resources, long discussions, long contributors) {}

  /** 当前正式复习指南 */
  public record StudyGuide(String contentMarkdown, String changeNote, LocalDateTime updatedAt) {}

  /** 管理员更新正式指南并声明已采纳建议 */
  public record UpdateStudyGuide(
      @Size(max = 12000) String contentMarkdown,
      @Size(max = 500) String changeNote,

      /** 成员提交指南参考的请求 */
      List<Long> incorporatedSubmissionIds) {}

  public record CreateGuideSubmission(@NotBlank @Size(max = 12000) String contentMarkdown) {}

  /** 指南参考的审核与展示模型 */
  public record GuideSubmission(
      long id, String contentMarkdown, String author, String status, LocalDateTime createdAt) {}
}
