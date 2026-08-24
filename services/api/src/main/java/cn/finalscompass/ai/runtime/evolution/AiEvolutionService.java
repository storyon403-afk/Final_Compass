package cn.finalscompass.ai.runtime.evolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 汇总技能、供应商、工作流和节点指标，并生成可供管理员审核的优化建议
 * 维护入口：新增指标维度改聚合方法；推荐规则升级时优先拆出独立策略组件
 */
@Service
public class AiEvolutionService {
  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public AiEvolutionService(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // 刷新远端配置或发现结果。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  @Transactional
  public RefreshResult refresh(long admin, LocalDate date) {
    if (date == null
        || date.isAfter(LocalDate.now())
        || date.isBefore(LocalDate.now().minusYears(1)))
      throw new IllegalArgumentException("AI Evolution metric date is invalid");
    String key = UUID.randomUUID().toString();
    var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.sql(
            "INSERT INTO ai_evolution_run(run_key,metric_date,trigger_type,triggered_by)"
                + " VALUES(:key,:date,'MANUAL',:admin)")
        .param("key", key)
        .param("date", date)
        .param("admin", admin)
        .update(holder, "id");
    long run = holder.getKey().longValue();
    try {
      delete(date);
      int skills = skills(date),
          providers = providers(date),
          workflows = workflows(date),
          nodes = nodes(date),
          recommendations = recommend(date);
      jdbc.sql(
              "UPDATE ai_evolution_run SET status='SUCCEEDED',completed_at=CURRENT_TIMESTAMP(6)"
                  + " WHERE id=:id")
          .param("id", run)
          .update();
      return new RefreshResult(key, date, skills, providers, workflows, nodes, recommendations);
    } catch (RuntimeException e) {
      throw e;
    }
  }

  // 汇总 AI 演进指标看板。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  public Dashboard dashboard() {
    Map<String, Object> summary =
        jdbc.sql(
                """
SELECT COALESCE(SUM(execution_count),0) skill_executions,COALESCE(SUM(failed_count),0) skill_failures,
  COALESCE(SUM(feedback_count),0) feedback_count,COALESCE(SUM(negative_feedback_count),0) negative_feedback_count,
  COALESCE(SUM(input_units+output_units),0) token_units,COALESCE(SUM(estimated_cost),0) estimated_cost
FROM ai_skill_daily_metric WHERE metric_date>=CURRENT_DATE-INTERVAL 30 DAY
""")
            .query()
            .singleRow();
    List<Map<String, Object>> skills =
        jdbc.sql(
                "SELECT *,CASE WHEN feedback_count=0 THEN 0 ELSE"
                    + " negative_feedback_count/feedback_count END negative_rate,CASE WHEN"
                    + " execution_count=0 THEN 0 ELSE failed_count/execution_count END failure_rate"
                    + " FROM ai_skill_daily_metric ORDER BY metric_date DESC,negative_rate"
                    + " DESC,failure_rate DESC LIMIT 200")
            .query()
            .listOfRows();
    List<Map<String, Object>> providers =
        jdbc.sql(
                "SELECT *,CASE WHEN invocation_count=0 THEN 0 ELSE"
                    + " (failed_count+timeout_count)/invocation_count END failure_rate FROM"
                    + " ai_provider_daily_metric ORDER BY metric_date DESC,failure_rate DESC LIMIT"
                    + " 100")
            .query()
            .listOfRows();
    List<Map<String, Object>> workflows =
        jdbc.sql(
                "SELECT *,CASE WHEN execution_count=0 THEN 0 ELSE failed_count/execution_count END"
                    + " failure_rate FROM ai_workflow_daily_metric ORDER BY metric_date"
                    + " DESC,failure_rate DESC LIMIT 100")
            .query()
            .listOfRows();
    List<Map<String, Object>> recommendations =
        jdbc.sql(
                "SELECT r.*,s.skill_key,v.version skill_version FROM"
                    + " ai_skill_optimization_recommendation r JOIN ai_runtime_skill s ON"
                    + " s.id=r.skill_id JOIN ai_runtime_skill_version v ON v.id=r.skill_version_id"
                    + " ORDER BY"
                    + " FIELD(r.status,'DRAFT','APPROVED','REJECTED','APPLIED'),r.created_at DESC"
                    + " LIMIT 200")
            .query()
            .listOfRows();
    return new Dashboard(summary, skills, providers, workflows, recommendations);
  }

  // 复核用户反馈并沉淀改进建议。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  public void review(long admin, long id, Review request) {
    if (request == null || !Set.of("APPROVED", "REJECTED").contains(request.status()))
      throw new IllegalArgumentException("AI Evolution review is invalid");
    int count =
        jdbc.sql(
                "UPDATE ai_skill_optimization_recommendation SET"
                    + " status=:status,reviewed_by=:admin,review_note=:note,reviewed_at=CURRENT_TIMESTAMP(6)"
                    + " WHERE id=:id AND status='DRAFT'")
            .param("status", request.status())
            .param("admin", admin)
            .param("note", clean(request.note()))
            .param("id", id)
            .update();
    if (count != 1)
      throw new IllegalStateException("Recommendation is unavailable or already reviewed");
  }

  // 删除业务数据。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  private void delete(LocalDate date) {
    jdbc.sql("DELETE FROM ai_skill_daily_metric WHERE metric_date=:date")
        .param("date", date)
        .update();
    jdbc.sql("DELETE FROM ai_provider_daily_metric WHERE metric_date=:date")
        .param("date", date)
        .update();
    jdbc.sql("DELETE FROM ai_workflow_node_daily_metric WHERE metric_date=:date")
        .param("date", date)
        .update();
    jdbc.sql("DELETE FROM ai_workflow_daily_metric WHERE metric_date=:date")
        .param("date", date)
        .update();
  }

  // 写入技能维度的每日统计。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  private int skills(LocalDate date) {
    return jdbc.sql(
            """
INSERT INTO ai_skill_daily_metric
SELECT :date,n.skill_id,n.skill_version_id,n.skill_key_snapshot,n.skill_version_snapshot,COUNT(*),
  SUM(n.status='SUCCEEDED'),SUM(n.status='FAILED'),COALESCE(f.feedback_count,0),COALESCE(f.negative_count,0),
  COALESCE(SUM(p.input_units),0),COALESCE(SUM(p.output_units),0),COALESCE(SUM(p.estimated_cost),0),
  AVG(CASE WHEN n.completed_at IS NULL OR n.started_at IS NULL THEN NULL ELSE TIMESTAMPDIFF(MICROSECOND,n.started_at,n.completed_at)/1000 END)
FROM ai_runtime_execution_node n
LEFT JOIN (
  SELECT execution_node_id,SUM(input_units) input_units,SUM(output_units) output_units,
    SUM(COALESCE(estimated_cost,0)) estimated_cost
  FROM ai_runtime_provider_invocation
  GROUP BY execution_node_id
) p ON p.execution_node_id=n.id
LEFT JOIN (
  SELECT skill_version_id,COUNT(DISTINCT feedback_id) feedback_count,
    COUNT(DISTINCT CASE WHEN f.helpful=FALSE THEN feedback_id END) negative_count
  FROM ai_feedback_skill_snapshot s
  JOIN ai_task_feedback f ON f.id=s.feedback_id
  WHERE DATE(f.created_at)=:date
  GROUP BY skill_version_id
) f ON f.skill_version_id=n.skill_version_id
WHERE n.skill_id IS NOT NULL AND DATE(n.created_at)=:date
GROUP BY n.skill_id,n.skill_version_id,n.skill_key_snapshot,n.skill_version_snapshot,
  f.feedback_count,f.negative_count
""")
        .param("date", date)
        .update();
  }

  // 写入供应商维度的每日统计。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  private int providers(LocalDate date) {
    return jdbc.sql(
            """
INSERT INTO ai_provider_daily_metric SELECT :date,p.provider_id,p.provider_model_id,p.provider_key_snapshot,p.model_key_snapshot,
  COUNT(*),SUM(p.status='SUCCEEDED'),SUM(p.status='FAILED'),SUM(p.status='TIMEOUT'),SUM(p.input_units),SUM(p.output_units),
  SUM(COALESCE(p.estimated_cost,0)),AVG(p.latency_ms) FROM ai_runtime_provider_invocation p WHERE DATE(p.started_at)=:date
  GROUP BY p.provider_id,p.provider_model_id,p.provider_key_snapshot,p.model_key_snapshot
""")
        .param("date", date)
        .update();
  }

  // 写入工作流维度的每日统计。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  private int workflows(LocalDate date) {
    return jdbc.sql(
            """
INSERT INTO ai_workflow_daily_metric
SELECT :date,e.workflow_key,e.workflow_version,COUNT(*),
  SUM(e.status='SUCCEEDED'),SUM(e.status='FAILED'),SUM(e.status='CANCELLED'),
  AVG(CASE WHEN e.completed_at IS NULL OR e.started_at IS NULL THEN NULL ELSE TIMESTAMPDIFF(MICROSECOND,e.started_at,e.completed_at)/1000 END)
FROM ai_runtime_execution e WHERE e.workflow_key IS NOT NULL AND DATE(e.created_at)=:date GROUP BY e.workflow_key,e.workflow_version
""")
        .param("date", date)
        .update();
  }

  // 写入节点维度的每日统计。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  private int nodes(LocalDate date) {
    return jdbc.sql(
            """
INSERT INTO ai_workflow_node_daily_metric
SELECT :date,e.workflow_key,e.workflow_version,n.node_key,n.node_type,COUNT(*),
  SUM(n.status='SUCCEEDED'),SUM(n.status='FAILED'),
  AVG(CASE WHEN n.completed_at IS NULL OR n.started_at IS NULL THEN NULL ELSE TIMESTAMPDIFF(MICROSECOND,n.started_at,n.completed_at)/1000 END)
FROM ai_runtime_execution_node n JOIN ai_runtime_execution e ON e.id=n.execution_id WHERE e.workflow_key IS NOT NULL AND DATE(n.created_at)=:date
  GROUP BY e.workflow_key,e.workflow_version,n.node_key,n.node_type
""")
        .param("date", date)
        .update();
  }

  // 根据运行指标生成优化建议。使用参数化 SQL 访问数据库，并将查询结果映射为领域对象
  private int recommend(LocalDate date) {
    List<Map<String, Object>> rows =
        jdbc.sql(
                "SELECT * FROM ai_skill_daily_metric WHERE metric_date=:date AND"
                    + " ((feedback_count>=3 AND negative_feedback_count/feedback_count>=0.15) OR"
                    + " (execution_count>=10 AND failed_count/execution_count>=0.10))")
            .param("date", date)
            .query()
            .listOfRows();
    int created = 0;
    for (var row : rows) {
      long feedback = ((Number) row.get("feedback_count")).longValue(),
          negative = ((Number) row.get("negative_feedback_count")).longValue(),
          executions = ((Number) row.get("execution_count")).longValue(),
          failed = ((Number) row.get("failed_count")).longValue();
      String type =
          feedback >= 3 && negative * 1.0 / feedback >= .15
              ? "NEGATIVE_FEEDBACK"
              : "EXECUTION_FAILURE";
      String recommendation =
          "NEGATIVE_FEEDBACK".equals(type)
              ? "审查负反馈关联 Trace，比较输入理解、Prompt 约束与输出 Schema；创建新 Skill 版本进行离线验证，不要直接覆盖当前版本。"
              : "检查失败节点的 Provider 响应、超时和输出契约；先补回归样本，再评估是否创建新版本。";
      int count =
          jdbc.sql(
                  """
                  INSERT IGNORE INTO ai_skill_optimization_recommendation(
                    recommendation_key,skill_id,skill_version_id,recommendation_type,
                    window_start,window_end,evidence,recommendation
                  )
                  VALUES(
                    :key,:skill,:version,:type,:date,:date,CAST(:evidence AS JSON),:recommendation
                  )
                  """)
              .param("key", UUID.randomUUID().toString())
              .param("skill", row.get("skill_id"))
              .param("version", row.get("skill_version_id"))
              .param("type", type)
              .param("date", date)
              .param(
                  "evidence",
                  write(
                      Map.of(
                          "executions",
                          executions,
                          "failed",
                          failed,
                          "feedback",
                          feedback,
                          "negativeFeedback",
                          negative)))
              .param("recommendation", recommendation)
              .update();
      created += count;
    }
    return created;
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
  private String clean(String value) {
    if (value == null || value.isBlank()) return null;
    String result = value.trim();
    if (result.length() > 2000) throw new IllegalArgumentException("Review note is too long");
    return result;
  }

  public record RefreshResult(
      String runKey,
      LocalDate metricDate,
      int skillRows,
      int providerRows,
      int workflowRows,
      int nodeRows,
      int recommendationsCreated) {}

  public record Dashboard(
      Map<String, Object> summary,
      List<Map<String, Object>> skills,
      List<Map<String, Object>> providers,
      List<Map<String, Object>> workflows,
      List<Map<String, Object>> recommendations) {}

  public record Review(String status, String note) {}
}
