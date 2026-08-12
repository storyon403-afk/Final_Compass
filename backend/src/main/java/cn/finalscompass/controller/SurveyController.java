package cn.finalscompass.controller;

import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/survey")
public class SurveyController {
  private final JdbcClient jdbc;
  private final AuthService auth;

  public SurveyController(JdbcClient jdbc, AuthService auth) {
    this.jdbc = jdbc;
    this.auth = auth;
  }

  @GetMapping
  public List<Map<String, Object>> questions(HttpServletRequest request) {
    auth.current(request);
    return jdbc.sql(
            "SELECT id,prompt,sort_order FROM survey_question WHERE active=TRUE ORDER BY"
                + " sort_order,id")
        .query()
        .listOfRows();
  }

  @PostMapping("/submissions")
  @Transactional
  public Map<String, Object> submit(
      HttpServletRequest request, @Valid @RequestBody Submission body) {
    var user = auth.current(request);
    if (user.isAdmin()) throw new IllegalArgumentException("管理员无需提交用户问卷");
    if (body.answers() == null || body.answers().isEmpty())
      throw new IllegalArgumentException("请完成问卷后再提交");
    var active =
        jdbc.sql("SELECT id,prompt FROM survey_question WHERE active=TRUE ORDER BY sort_order,id")
            .query(QuestionRow.class)
            .list();
    if (body.answers().size() != active.size())
      throw new IllegalArgumentException("问卷内容已更新，请刷新后重新填写");
    var holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
    jdbc.sql("INSERT INTO survey_submission(user_id,overall_suggestion) VALUES (:user,:suggestion)")
        .param("user", user.id())
        .param("suggestion", clean(body.overallSuggestion()))
        .update(holder, "id");
    Number generatedKey = holder.getKey();
    if (generatedKey == null) throw new IllegalStateException("未能取得问卷提交编号");
    long submissionId = generatedKey.longValue();
    for (QuestionRow question : active) {
      Answer answer =
          body.answers().stream()
              .filter(item -> item.questionId() == question.id())
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("请回答全部问题"));
      jdbc.sql(
              """
INSERT INTO survey_answer(submission_id,question_id,question_snapshot,rating,suggestion)
VALUES (:submission,:question,:snapshot,:rating,:suggestion)
""")
          .param("submission", submissionId)
          .param("question", question.id())
          .param("snapshot", question.prompt())
          .param("rating", answer.rating())
          .param("suggestion", clean(answer.suggestion()))
          .update();
    }
    return Map.of("id", submissionId, "message", "感谢你的真实反馈，我们会认真阅读每一条建议");
  }

  @GetMapping("/admin")
  public Map<String, Object> adminOverview(HttpServletRequest request) {
    auth.requireAdmin(request);
    var questions =
        jdbc.sql("SELECT id,prompt,sort_order,active FROM survey_question ORDER BY sort_order,id")
            .query()
            .listOfRows();
    var submissions =
        jdbc.sql(
                """
                SELECT s.id,u.username,s.overall_suggestion,s.created_at
                FROM survey_submission s JOIN app_user u ON u.id=s.user_id
                ORDER BY s.created_at DESC LIMIT 200
                """)
            .query()
            .listOfRows();
    var result = new ArrayList<Map<String, Object>>();
    for (var submission : submissions) {
      var answers =
          jdbc.sql(
                  """
                  SELECT question_id,question_snapshot,rating,suggestion
                  FROM survey_answer WHERE submission_id=:id ORDER BY id
                  """)
              .param("id", submission.get("id"))
              .query()
              .listOfRows();
      var item = new java.util.LinkedHashMap<String, Object>(submission);
      item.put("answers", answers);
      result.add(item);
    }
    return Map.of("questions", questions, "submissions", result);
  }

  @PutMapping("/admin/questions")
  @Transactional
  public Map<String, String> updateQuestions(
      HttpServletRequest request, @Valid @RequestBody QuestionUpdate body) {
    auth.requireAdmin(request);
    if (body.questions() == null || body.questions().isEmpty())
      throw new IllegalArgumentException("问卷至少需要一道题");
    if (body.questions().size() > 12) throw new IllegalArgumentException("问卷最多设置 12 道题");
    jdbc.sql("UPDATE survey_question SET active=FALSE").update();
    int order = 10;
    for (String prompt : body.questions()) {
      String value = prompt == null ? "" : prompt.trim();
      if (value.isBlank() || value.length() > 300)
        throw new IllegalArgumentException("题目不能为空且最多 300 字");
      jdbc.sql("INSERT INTO survey_question(prompt,sort_order,active) VALUES (:prompt,:sort,TRUE)")
          .param("prompt", value)
          .param("sort", order)
          .update();
      order += 10;
    }
    return Map.of("message", "问卷内容已更新");
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record QuestionRow(long id, String prompt) {}

  public record Answer(
      long questionId, @Min(1) @Max(5) int rating, @Size(max = 1000) String suggestion) {}

  public record Submission(
      List<@Valid Answer> answers, @Size(max = 2000) String overallSuggestion) {}

  public record QuestionUpdate(List<@NotBlank @Size(max = 300) String> questions) {}
}
