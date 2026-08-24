package cn.finalscompass.survey.domain;

import java.util.List;
import java.util.Map;

/** 问卷用例所需的持久化操作 */
public interface SurveyRepository {
  List<Map<String, Object>> findActiveQuestionViews();
  List<Question> findActiveQuestions();
  long createSubmission(long userId, String suggestion);
  void addAnswer(long submissionId, Question question, int rating, String suggestion);
  List<Map<String, Object>> findAllQuestionViews();
  List<Map<String, Object>> findRecentSubmissions();
  List<Map<String, Object>> findAnswers(long submissionId);
  void deactivateQuestions();
  void addQuestion(String prompt, int sortOrder);

  record Question(long id, String prompt) {}
}
