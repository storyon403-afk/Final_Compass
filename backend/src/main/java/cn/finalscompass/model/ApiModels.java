package cn.finalscompass.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}

    public record AnonymousProfile(String publicId, String nickname) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record BetaAccessRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Email @Size(max = 254) String confirmEmail,
            @NotBlank @Pattern(regexp = "^\\+86(1[3-9]\\d{9})$", message = "手机号须为 +86 格式，例如 +8613812345678") String phone) {}
    public record BetaAccessChallenge(long requestId, String email, LocalDateTime expiresAt) {}
    public record BetaAccessVerification(long requestId,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "请输入 6 位数字验证码") String code) {}
    public record AuthProfile(String token, String username, String displayName, String role) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 6, max = 72) String newPassword) {}
    public record Announcement(String content, boolean enabled, LocalDateTime updatedAt) {}
    public record UpdateAnnouncement(@NotBlank @Size(max = 1000) String content, boolean enabled) {}
    public record College(long id, String name) {}
    public record CreateCollege(@NotBlank @Size(max = 100) String name) {}
    public record Course(long id, String slug, String name, String code, String category, String college,
                         String programName, String courseType) {}
    public record CreateCourse(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(max = 100) String college,
            @Size(max = 80) String programName,
            @Pattern(regexp = "专业课|非专业课", message = "课程类型只能是专业课或非专业课") String courseType) {}
    public record Teacher(long id, String slug, String name, String college, long resourceCount, long postCount) {}
    public record CreateTeacher(
            @NotBlank @Size(max = 40) String name,
            @NotBlank @Size(max = 100) String college) {}
    public record Resource(long id, String title, String type, String description, String originalName,
                           long fileSize, int downloads, int thanks, String contributor, LocalDateTime createdAt) {}
    public record Discussion(long id, String author, String content, int likes, long replies,
                             Long parentId, LocalDateTime createdAt) {}
    public record CreateDiscussion(
            @NotBlank @Size(max = 500) String content,
            Long parentId) {}
    public record CircleSummary(long resources, long discussions, long contributors) {}
    public record StudyGuide(String contentMarkdown, String changeNote, LocalDateTime updatedAt) {}
    public record UpdateStudyGuide(@Size(max = 12000) String contentMarkdown,
                                   @Size(max = 500) String changeNote,
                                   List<Long> incorporatedSubmissionIds) {}
    public record CreateGuideSubmission(@NotBlank @Size(max = 12000) String contentMarkdown) {}
    public record GuideSubmission(long id, String contentMarkdown, String author, String status,
                                  LocalDateTime createdAt) {}
}
