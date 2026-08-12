package cn.finalscompass.controller;

import cn.finalscompass.model.ApiModels.Announcement;
import cn.finalscompass.model.ApiModels.UpdateAnnouncement;
import cn.finalscompass.service.AccountAllocationService;
import cn.finalscompass.service.ActivityService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/system")
public class SystemController {
  private final JdbcClient jdbc;
  private final AuthService auth;
  private final ActivityService activity;
  private final AccountAllocationService accounts;

  public SystemController(
      JdbcClient jdbc,
      AuthService auth,
      ActivityService activity,
      AccountAllocationService accounts) {
    this.jdbc = jdbc;
    this.auth = auth;
    this.activity = activity;
    this.accounts = accounts;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    jdbc.sql("SELECT 1").query(Integer.class).single();
    return Map.of("status", "ok", "time", Instant.now().toString());
  }

  @GetMapping("/announcement")
  public Announcement announcement() {
    return jdbc.sql(
            """
            SELECT content,enabled,updated_at
            FROM system_announcement WHERE id=1
            """)
        .query(Announcement.class)
        .single();
  }

  @PutMapping("/announcement")
  public Announcement updateAnnouncement(
      HttpServletRequest request, @Valid @RequestBody UpdateAnnouncement update) {
    var admin = auth.requireAdmin(request);
    jdbc.sql(
            """
            UPDATE system_announcement
            SET content=:content,enabled=:enabled,updated_by=:admin,updated_at=NOW()
            WHERE id=1
            """)
        .param("content", update.content().trim())
        .param("enabled", update.enabled())
        .param("admin", admin.id())
        .update();
    return announcement();
  }

  @GetMapping("/metrics")
  public Map<String, Object> metrics(HttpServletRequest request) {
    auth.requireAdmin(request);
    return jdbc.sql(
            """
            SELECT
              (SELECT COUNT(*) FROM course WHERE active=TRUE) courses,
              (SELECT COUNT(*) FROM teacher) teachers,
              (SELECT COUNT(*) FROM resource WHERE status='PUBLISHED') published_resources,
              (SELECT COUNT(*) FROM resource WHERE status='PENDING') pending_resources,
              (SELECT COUNT(*) FROM discussion WHERE status='VISIBLE') visible_discussions,
              (SELECT COUNT(*) FROM discussion WHERE status='PENDING') pending_discussions
            """)
        .query()
        .singleRow();
  }

  @GetMapping("/moderation")
  public List<Map<String, Object>> moderation(HttpServletRequest request) {
    auth.requireAdmin(request);
    return jdbc.sql(
            """
SELECT 'RESOURCE' item_type,id,title summary,created_at FROM resource WHERE status='PENDING'
UNION ALL
SELECT 'DISCUSSION' item_type,id,LEFT(content,120) summary,created_at FROM discussion WHERE status='PENDING'
UNION ALL
SELECT 'GUIDE_SUBMISSION' item_type,id,LEFT(content_markdown,120) summary,created_at FROM guide_submission WHERE status='PENDING'
ORDER BY created_at
""")
        .query()
        .listOfRows();
  }

  @GetMapping("/beta-access")
  public List<Map<String, Object>> betaAccessRequests(HttpServletRequest request) {
    auth.requireAdmin(request);
    jdbc.sql(
            "UPDATE beta_access_request SET status='EXPIRED' WHERE status IN"
                + " ('CREATED','CODE_SENT') AND expires_at<=NOW()")
        .update();
    accounts.ensureVerifiedReservations();
    return jdbc.sql(
            """
SELECT r.id,r.email,r.phone,r.status,r.failed_attempts,r.expires_at,r.verified_at,r.created_at,
       r.reviewed_at,r.rejection_reason,a.reserved_username suggested_username
FROM beta_access_request r LEFT JOIN account_reservation a ON a.request_id=r.id
ORDER BY r.created_at DESC LIMIT 200
""")
        .query()
        .listOfRows();
  }

  @PostMapping("/moderation/{type}/{id}")
  @Transactional
  public Map<String, String> moderate(
      HttpServletRequest request,
      @PathVariable String type,
      @PathVariable long id,
      @RequestParam String decision) {
    var admin = auth.requireAdmin(request);
    String normalizedType = type.toUpperCase();
    String normalizedDecision = decision.toUpperCase();
    String table;
    String status;
    if ("RESOURCE".equals(normalizedType)) {
      table = "resource";
      status = "APPROVE".equals(normalizedDecision) ? "PUBLISHED" : "REJECTED";
    } else if ("DISCUSSION".equals(normalizedType)) {
      table = "discussion";
      status = "APPROVE".equals(normalizedDecision) ? "VISIBLE" : "REMOVED";
    } else if ("GUIDE_SUBMISSION".equals(normalizedType)) {
      table = "guide_submission";
      status = "APPROVE".equals(normalizedDecision) ? "APPROVED" : "REJECTED";
    } else throw new IllegalArgumentException("不支持的审核类型");
    if (!"APPROVE".equals(normalizedDecision) && !"REJECT".equals(normalizedDecision))
      throw new IllegalArgumentException("decision 只能是 APPROVE 或 REJECT");
    Long beneficiaryId =
        "APPROVE".equals(normalizedDecision) ? contributionOwner(normalizedType, id) : null;
    int changed =
        jdbc.sql("UPDATE " + table + " SET status=:status WHERE id=:id AND status='PENDING'")
            .param("status", status)
            .param("id", id)
            .update();
    if (changed != 1) throw new IllegalArgumentException("待审核记录不存在或已处理");
    jdbc.sql(
            "INSERT INTO moderation_audit(item_type,item_id,decision,reviewer_id) VALUES"
                + " (:type,:id,:decision,:reviewer)")
        .param("type", normalizedType)
        .param("id", id)
        .param("decision", normalizedDecision)
        .param("reviewer", admin.id())
        .update();
    if ("GUIDE_SUBMISSION".equals(normalizedType)) {
      jdbc.sql("UPDATE guide_submission SET reviewed_at=NOW() WHERE id=:id")
          .param("id", id)
          .update();
    }
    if (beneficiaryId != null) activity.recordApproved(beneficiaryId, normalizedType, id);
    return Map.of("status", status);
  }

  private Long contributionOwner(String type, long id) {
    String sql =
        switch (type) {
          case "RESOURCE" ->
              "SELECT u.app_user_id FROM resource x JOIN anonymous_user u ON u.id=x.uploader_id"
                  + " WHERE x.id=:id";
          case "DISCUSSION" ->
              "SELECT u.app_user_id FROM discussion x JOIN anonymous_user u ON u.id=x.author_id"
                  + " WHERE x.id=:id";
          case "GUIDE_SUBMISSION" ->
              "SELECT u.app_user_id FROM guide_submission x JOIN anonymous_user u ON"
                  + " u.id=x.author_id WHERE x.id=:id";
          default -> throw new IllegalArgumentException("不支持的审核类型");
        };
    return jdbc.sql(sql).param("id", id).query(Long.class).optional().orElse(null);
  }

  @DeleteMapping("/discussions/{id}")
  public Map<String, String> removeDiscussion(HttpServletRequest request, @PathVariable long id) {
    var admin = auth.requireAdmin(request);
    int changed =
        jdbc.sql("UPDATE discussion SET status='REMOVED' WHERE id=:id AND status='VISIBLE'")
            .param("id", id)
            .update();
    if (changed != 1) throw new IllegalArgumentException("帖子不存在或已删除");
    jdbc.sql(
            "INSERT INTO moderation_audit(item_type,item_id,decision,reviewer_id) VALUES"
                + " ('DISCUSSION',:id,'REJECT',:reviewer)")
        .param("id", id)
        .param("reviewer", admin.id())
        .update();
    return Map.of("status", "REMOVED");
  }
}
