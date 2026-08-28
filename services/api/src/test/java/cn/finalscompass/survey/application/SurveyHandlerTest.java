package cn.finalscompass.survey.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import cn.finalscompass.survey.api.SurveyModels.*;
import cn.finalscompass.survey.domain.SurveyRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurveyHandlerTest {
  private final SurveyRepository repository = mock(SurveyRepository.class);
  private final SurveyHandler handler = new SurveyHandler(repository, new AuthorizationPolicy());
  private final AuthService.CurrentUser user =
      new AuthService.CurrentUser(7, "user", "User", "USER", "hash", "token", false);
  private final AuthService.CurrentUser admin =
      new AuthService.CurrentUser(1, "admin", "Admin", "ADMIN", "hash", "token", false);

  @Test
  void submitsEveryActiveQuestionUsingSnapshot() {
    var q1 = new SurveyRepository.Question(10, "体验如何");
    var q2 = new SurveyRepository.Question(11, "是否稳定");
    when(repository.findActiveQuestions()).thenReturn(List.of(q1, q2));
    when(repository.createSubmission(7, "总体建议")).thenReturn(22L);

    var result = handler.submit(user, new Submission(List.of(new Answer(11, 4, "稳定"), new Answer(10, 5, null)), " 总体建议 "));

    assertThat(result).containsEntry("id", 22L);
    verify(repository).addAnswer(22, q1, 5, null);
    verify(repository).addAnswer(22, q2, 4, "稳定");
  }

  @Test
  void rejectsStaleOrIncompleteSubmission() {
    when(repository.findActiveQuestions()).thenReturn(List.of(new SurveyRepository.Question(10, "问题")));
    assertThatThrownBy(() -> handler.submit(user, new Submission(List.of(new Answer(99, 5, null)), null)))
        .hasMessageContaining("回答全部问题");
  }

  @Test
  void onlyAdminMayReplaceQuestions() {
    assertThatThrownBy(() -> handler.updateQuestions(user, new QuestionUpdate(List.of("新问题"))))
        .hasMessageContaining("只有管理员");
    handler.updateQuestions(admin, new QuestionUpdate(List.of(" 第一题 ", "第二题")));
    verify(repository).deactivateQuestions();
    verify(repository).addQuestion("第一题", 10);
    verify(repository).addQuestion("第二题", 20);
  }
}
