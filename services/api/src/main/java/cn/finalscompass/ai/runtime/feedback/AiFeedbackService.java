package cn.finalscompass.ai.runtime.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 接收用户评分与反馈，关联执行链路，并支持管理员复核和改进候选答案
 * 维护入口：反馈状态、审核流程或差异计算规则都集中在这里维护
 */
@Service
public class AiFeedbackService {
  private static final Set<String> TRIGGERS =
      Set.of(
          "NEXT_REQUEST", "TASK_COMPLETION", "FILE_GENERATION", "MULTI_STEP_TASK", "SIMPLE_ANSWER");
  private static final Set<String> ISSUES =
      Set.of(
          "UNDERSTANDING_ERROR",
          "CONTENT_ERROR",
          "INCOMPLETE",
          "POOR_REASONING",
          "FORMAT_LAYOUT",
          "STYLE_MISMATCH",
          "SLOW_RESPONSE",
          "OTHER");
  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final SecureRandom random = new SecureRandom();

  public AiFeedbackService(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // 提交反馈改进候选答案。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  public Offer offer(long user, OfferRequest request) {
    if (request == null || !TRIGGERS.contains(request.triggerType()))
      throw new IllegalArgumentException("AI feedback trigger is invalid");
    Context context = context(user, request.executionId(), request.documentJobKey());
    double rate = rate(request.triggerType());
    Optional<Offer> existing = existing(user, context);
    if (existing.isPresent()) return existing.get();
    Long recent =
        jdbc.sql(
                "SELECT COUNT(*) FROM ai_feedback_prompt WHERE user_id=:user AND"
                    + " offered_at>=CURRENT_TIMESTAMP(6)-INTERVAL 6 HOUR")
            .param("user", user)
            .query(Long.class)
            .single();
    if (recent > 0) return new Offer(false, null, rate, null);
    if (random.nextDouble() >= rate) return new Offer(false, null, rate, null);
    String key = UUID.randomUUID().toString();
    jdbc.sql(
            "INSERT INTO"
                + " ai_feedback_prompt(prompt_key,user_id,execution_id,document_job_id,trigger_type,sample_rate,expires_at)"
                + " VALUES(:key,:user,:execution,:document,:trigger,:rate,:expires)")
        .param("key", key)
        .param("user", user)
        .param("execution", context.executionId())
        .param("document", context.documentJobId())
        .param("trigger", request.triggerType())
        .param("rate", rate)
        .param("expires", LocalDateTime.now().plusDays(7))
        .update();
    return new Offer(true, key, rate, LocalDateTime.now().plusDays(7));
  }

  // 把反馈标记为无需处理。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  public void dismiss(long user, String promptKey) {
    int count =
        jdbc.sql(
                "UPDATE ai_feedback_prompt SET status='DISMISSED',resolved_at=CURRENT_TIMESTAMP(6)"
                    + " WHERE prompt_key=:key AND user_id=:user AND status='OFFERED'")
            .param("key", promptKey)
            .param("user", user)
            .update();
    if (count != 1) throw new IllegalArgumentException("AI feedback prompt is unavailable");
  }

  // 提交用户反馈。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  // 可升级：该方法职责较多，后续可按校验、执行和结果持久化拆分
  @Transactional
  public FeedbackResult submit(long user, SubmitRequest request) {
    validate(request);
    Prompt prompt =
        jdbc.sql(
                "SELECT id,user_id,execution_id,document_job_id,status,expires_at FROM"
                    + " ai_feedback_prompt WHERE prompt_key=:key AND user_id=:user FOR UPDATE")
            .param("key", request.promptKey())
            .param("user", user)
            .query(Prompt.class)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("AI feedback prompt is unavailable"));
    if (!"OFFERED".equals(prompt.status()) || prompt.expiresAt().isBefore(LocalDateTime.now()))
      throw new IllegalStateException("AI feedback prompt has expired or was resolved");
    Long template =
        prompt.documentJobId() == null
            ? null
            : jdbc.sql("SELECT template_id FROM document_generation_job WHERE id=:id")
                .param("id", prompt.documentJobId())
                .query(Long.class)
                .optional()
                .orElse(null);
    var keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.sql(
            "INSERT INTO"
                + " ai_task_feedback(prompt_id,user_id,execution_id,document_job_id,template_id,helpful,primary_issue,issue_tags,comment)"
                + " VALUES(:prompt,:user,:execution,:document,:template,:helpful,:issue,CAST(:tags"
                + " AS JSON),:comment)")
        .param("prompt", prompt.id())
        .param("user", user)
        .param("execution", prompt.executionId())
        .param("document", prompt.documentJobId())
        .param("template", template)
        .param("helpful", request.helpful())
        .param("issue", request.primaryIssue())
        .param("tags", write(request.issueTags()))
        .param("comment", clean(request.comment(), 4000))
        .update(keys, "id");
    if (keys.getKey() == null) throw new IllegalStateException("AI feedback id was not generated");
    long id = keys.getKey().longValue();
    snapshot(id, prompt.executionId(), request);
    int changed =
        jdbc.sql(
                "UPDATE ai_feedback_prompt SET status='SUBMITTED',resolved_at=CURRENT_TIMESTAMP(6)"
                    + " WHERE id=:id AND status='OFFERED'")
            .param("id", prompt.id())
            .update();
    if (changed != 1)
      throw new IllegalStateException("AI feedback prompt was resolved concurrently");
    return new FeedbackResult(id, "感谢反馈。你的评价已与本次 AI Trace 关联。", !request.helpful());
  }

  // 查询待管理员处理的反馈队列。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  public List<Map<String, Object>> adminQueue() {
    return jdbc.sql(
            """
SELECT o.id,o.status,o.priority,o.issue_type,o.created_at,f.id feedback_id,f.helpful,f.primary_issue,f.comment,
       e.execution_id,e.trace_id,e.workflow_key,e.workflow_version,s.skill_key_snapshot,s.skill_version_snapshot
FROM ai_pending_skill_optimization o JOIN ai_task_feedback f ON f.id=o.feedback_id
JOIN ai_feedback_skill_snapshot s ON s.feedback_id=f.id AND s.skill_version_id=o.skill_version_id
LEFT JOIN ai_runtime_execution e ON e.id=f.execution_id
ORDER BY FIELD(o.status,'OPEN','IN_REVIEW','RESOLVED','DISMISSED'),o.priority DESC,o.created_at DESC LIMIT 500
""")
        .query()
        .listOfRows();
  }

  // 根据候选评分生成最终路由决策。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  public void decide(long admin, long id, Decision request) {
    if (request == null || !Set.of("IN_REVIEW", "RESOLVED", "DISMISSED").contains(request.status()))
      throw new IllegalArgumentException("Optimization decision is invalid");
    int count =
        jdbc.sql(
                "UPDATE ai_pending_skill_optimization SET"
                    + " status=:status,assignee_user_id=:admin,resolution_note=:note,resolved_at=CASE"
                    + " WHEN :status IN ('RESOLVED','DISMISSED') THEN CURRENT_TIMESTAMP(6) ELSE"
                    + " NULL END WHERE id=:id")
            .param("status", request.status())
            .param("admin", admin)
            .param("note", clean(request.note(), 2000))
            .param("id", id)
            .update();
    if (count != 1) throw new IllegalArgumentException("Optimization item does not exist");
  }

  /**
   * 读取最近一次发现快照
   * 实现上，使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
   *
   * @param feedback 数据库中的反馈记录
   * @param execution 反馈关联的执行记录
   * @param request 本次调用的请求参数
   */
  private void snapshot(long feedback, Long execution, SubmitRequest request) {
    if (execution == null) return;
    jdbc.sql(
            "INSERT INTO ai_feedback_skill_snapshot SELECT"
                + " :feedback,n.id,n.skill_id,n.skill_version_id,n.skill_key_snapshot,n.skill_version_snapshot,n.status"
                + " FROM ai_runtime_execution_node n WHERE n.execution_id=:execution AND n.skill_id"
                + " IS NOT NULL")
        .param("feedback", feedback)
        .param("execution", execution)
        .update();
    jdbc.sql(
            "INSERT INTO ai_feedback_provider_snapshot SELECT"
                + " :feedback,p.id,p.provider_key_snapshot,p.model_key_snapshot,p.status,p.latency_ms,p.input_units,p.output_units"
                + " FROM ai_runtime_provider_invocation p JOIN ai_runtime_execution_node n ON"
                + " n.id=p.execution_node_id WHERE n.execution_id=:execution")
        .param("feedback", feedback)
        .param("execution", execution)
        .update();
    jdbc.sql(
            "INSERT INTO ai_feedback_tool_snapshot SELECT"
                + " :feedback,n.id,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(n.metadata,'$.toolKey')),n.node_key),n.status"
                + " FROM ai_runtime_execution_node n WHERE n.execution_id=:execution AND"
                + " n.node_type='TOOL'")
        .param("feedback", feedback)
        .param("execution", execution)
        .update();
    if (!request.helpful())
      jdbc.sql(
              "INSERT INTO"
                  + " ai_pending_skill_optimization(feedback_id,skill_id,skill_version_id,issue_type,priority)"
                  + " SELECT :feedback,s.skill_id,s.skill_version_id,:issue,CASE WHEN :issue IN"
                  + " ('CONTENT_ERROR','UNDERSTANDING_ERROR') THEN 80 ELSE 50 END FROM"
                  + " ai_feedback_skill_snapshot s WHERE s.feedback_id=:feedback")
          .param("feedback", feedback)
          .param("issue", request.primaryIssue())
          .update();
  }

  /**
   * 构造运行时工具执行上下文
   * 实现上，使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
   *
   * @param user 用户 ID
   * @param executionKey execution 的业务唯一键
   * @param documentKey document 的业务唯一键
   * @return 处理后的业务结果
   */
  private Context context(long user, String executionKey, String documentKey) {
    Long execution = null, document = null;
    if (documentKey != null && !documentKey.isBlank()) {
      Map<String, Object> row =
          jdbc
              .sql(
                  "SELECT j.id document_id,e.id execution_id FROM document_generation_job j LEFT"
                      + " JOIN ai_runtime_execution e ON e.trace_id=j.trace_id WHERE j.job_key=:key"
                      + " AND j.user_id=:user")
              .param("key", documentKey)
              .param("user", user)
              .query()
              .listOfRows()
              .stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.FORBIDDEN, "Document feedback access denied"));
      document = ((Number) row.get("document_id")).longValue();
      Object e = row.get("execution_id");
      if (e instanceof Number n) execution = n.longValue();
    }
    if (executionKey != null && !executionKey.isBlank()) {
      Long found =
          jdbc.sql("SELECT id FROM ai_runtime_execution WHERE execution_id=:key AND user_id=:user")
              .param("key", executionKey)
              .param("user", user)
              .query(Long.class)
              .optional()
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.FORBIDDEN, "AI feedback access denied"));
      if (execution != null && !execution.equals(found))
        throw new IllegalArgumentException("AI feedback contexts do not match");
      execution = found;
    }
    if (execution == null && document == null)
      throw new IllegalArgumentException("AI feedback context is required");
    return new Context(execution, document);
  }

  // 查询已经存在的配置记录。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  private Optional<Offer> existing(long user, Context context) {
    String sql =
        "SELECT prompt_key,sample_rate,expires_at FROM ai_feedback_prompt WHERE user_id=:user AND"
            + " status='OFFERED' AND expires_at>CURRENT_TIMESTAMP(6) AND ";
    JdbcClient.StatementSpec statement;
    if (context.executionId() != null && context.documentJobId() != null)
      statement =
          jdbc.sql(
                  sql
                      + "(execution_id=:execution OR document_job_id=:document) ORDER BY offered_at"
                      + " DESC LIMIT 1")
              .param("user", user)
              .param("execution", context.executionId())
              .param("document", context.documentJobId());
    else if (context.executionId() != null)
      statement =
          jdbc.sql(sql + "execution_id=:execution ORDER BY offered_at DESC LIMIT 1")
              .param("user", user)
              .param("execution", context.executionId());
    else
      statement =
          jdbc.sql(sql + "document_job_id=:document ORDER BY offered_at DESC LIMIT 1")
              .param("user", user)
              .param("document", context.documentJobId());
    List<Map<String, Object>> rows = statement.query().listOfRows();
    if (rows.isEmpty()) return Optional.empty();
    var row = rows.getFirst();
    return Optional.of(
        new Offer(
            true,
            (String) row.get("prompt_key"),
            ((Number) row.get("sample_rate")).doubleValue(),
            (LocalDateTime) row.get("expires_at")));
  }

  // 记录用户对回答的评分
  private double rate(String trigger) {
    return switch (trigger) {
      case "FILE_GENERATION", "MULTI_STEP_TASK" -> .50;
      case "SIMPLE_ANSWER" -> .05;
      default -> .20;
    };
  }

  // 校验定义及其关联配置
  private void validate(SubmitRequest request) {
    if (request == null
        || request.promptKey() == null
        || request.promptKey().isBlank()
        || request.issueTags() == null
        || request.issueTags().size() > 10
        || !request.issueTags().stream().allMatch(ISSUES::contains)
        || request.comment() != null && request.comment().length() > 4000
        || request.helpful() && request.primaryIssue() != null
        || !request.helpful() && !ISSUES.contains(request.primaryIssue()))
      throw new IllegalArgumentException("AI feedback is invalid");
  }

  // 把对象序列化为 JSON。通过 Jackson 完成 JSON 的解析或序列化
  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // 清理并限制外部文本长度
  private String clean(String value, int max) {
    if (value == null || value.isBlank()) return null;
    String result = value.trim();
    if (result.length() > max) throw new IllegalArgumentException("Feedback text is too long");
    return result;
  }

  public record OfferRequest(String executionId, String documentJobKey, String triggerType) {}

  public record Offer(
      boolean offered, String promptKey, double sampleRate, LocalDateTime expiresAt) {}

  public record SubmitRequest(
      String promptKey,
      boolean helpful,
      String primaryIssue,
      Set<String> issueTags,
      String comment) {
    public SubmitRequest {
      issueTags = issueTags == null ? Set.of() : Set.copyOf(issueTags);
    }
  }

  public record FeedbackResult(long feedbackId, String message, boolean optimizationQueued) {}

  public record Decision(String status, String note) {}

  private record Context(Long executionId, Long documentJobId) {}

  private record Prompt(
      long id,
      long userId,
      Long executionId,
      Long documentJobId,
      String status,
      LocalDateTime expiresAt) {}
}
