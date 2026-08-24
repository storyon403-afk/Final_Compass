package cn.finalscompass.survey.infrastructure;

import cn.finalscompass.survey.domain.SurveyRepository;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSurveyRepository implements SurveyRepository {
  private final JdbcClient jdbc;
  public JdbcSurveyRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

  @Override public List<Map<String,Object>> findActiveQuestionViews() {
    return jdbc.sql("SELECT id,prompt,sort_order FROM survey_question WHERE active=TRUE ORDER BY sort_order,id")
        .query().listOfRows();
  }
  @Override public List<Question> findActiveQuestions() {
    return jdbc.sql("SELECT id,prompt FROM survey_question WHERE active=TRUE ORDER BY sort_order,id")
        .query(Question.class).list();
  }
  @Override public long createSubmission(long userId,String suggestion) {
    var keys=new GeneratedKeyHolder();
    jdbc.sql("INSERT INTO survey_submission(user_id,overall_suggestion) VALUES (:user,:suggestion)")
        .param("user",userId).param("suggestion",suggestion).update(keys,"id");
    Number key=keys.getKey();
    if(key==null) throw new IllegalStateException("未能取得问卷提交编号");
    return key.longValue();
  }
  @Override public void addAnswer(long submissionId,Question question,int rating,String suggestion) {
    jdbc.sql("""
        INSERT INTO survey_answer(submission_id,question_id,question_snapshot,rating,suggestion)
        VALUES (:submission,:question,:snapshot,:rating,:suggestion)
        """).param("submission",submissionId).param("question",question.id())
        .param("snapshot",question.prompt()).param("rating",rating).param("suggestion",suggestion).update();
  }
  @Override public List<Map<String,Object>> findAllQuestionViews() {
    return jdbc.sql("SELECT id,prompt,sort_order,active FROM survey_question ORDER BY sort_order,id")
        .query().listOfRows();
  }
  @Override public List<Map<String,Object>> findRecentSubmissions() {
    return jdbc.sql("""
        SELECT s.id,u.username,s.overall_suggestion,s.created_at
        FROM survey_submission s JOIN app_user u ON u.id=s.user_id
        ORDER BY s.created_at DESC LIMIT 200
        """).query().listOfRows();
  }
  @Override public List<Map<String,Object>> findAnswers(long submissionId) {
    return jdbc.sql("""
        SELECT question_id,question_snapshot,rating,suggestion
        FROM survey_answer WHERE submission_id=:id ORDER BY id
        """).param("id",submissionId).query().listOfRows();
  }
  @Override public void deactivateQuestions() {
    jdbc.sql("UPDATE survey_question SET active=FALSE").update();
  }
  @Override public void addQuestion(String prompt,int sortOrder) {
    jdbc.sql("INSERT INTO survey_question(prompt,sort_order,active) VALUES (:prompt,:sort,TRUE)")
        .param("prompt",prompt).param("sort",sortOrder).update();
  }
}
