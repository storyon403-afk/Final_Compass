package cn.finalscompass.questionvine;

import static cn.finalscompass.questionvine.QuestionVineModels.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import cn.finalscompass.message.SiteMessageService;
import cn.finalscompass.service.ActionRateLimitService;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionVineServiceTest {
  private final QuestionVineRepository repository = mock(QuestionVineRepository.class);
  private final SiteMessageService messages = mock(SiteMessageService.class);
  private final ActionRateLimitService limits = mock(ActionRateLimitService.class);
  private final QuestionVineService service =
      new QuestionVineService(repository, new AuthorizationPolicy(), messages, limits);
  private final AuthService.CurrentUser user =
      new AuthService.CurrentUser(7, "user", "User", "USER", "hash", "token", false);

  @Test
  void limitsAndCreatesTopicWithNextSequence() {
    var input = new CreateTopic("题目", "课程", List.of("标签"), "正文");
    var topic = new Topic(12, 4, "题目", "课程", List.of("标签"), "2026.08.29", "正文", "OPEN", "匿名", List.of());
    when(repository.nextSequence()).thenReturn(4);
    when(repository.create(eq(7L), anyString(), same(input), eq(4))).thenReturn(12L);
    when(repository.topic(12)).thenReturn(topic);

    org.assertj.core.api.Assertions.assertThat(service.create(user, input)).isSameAs(topic);
    verify(limits).questionTopic(7);
    verify(repository).lockTopics();
  }

  @Test
  void rejectsReplyWhoseParentBelongsToAnotherTopic() {
    when(repository.topicOwner(12)).thenReturn(new QuestionVineRepository.TopicOwner(9L, 4));
    when(repository.answerOwner(99)).thenReturn(new QuestionVineRepository.AnswerOwner(8L, 13));

    assertThatThrownBy(() -> service.answer(user, 12, new CreateAnswer("回复", 99L)))
        .hasMessageContaining("回复目标不属于");
    verify(limits).questionAnswer(7);
    verify(repository, never()).addAnswer(anyLong(), any(), anyLong(), anyString(), anyString());
  }

  @Test
  void notifiesTopicOwnerAfterAcceptedTopLevelAnswer() {
    when(repository.topicOwner(12)).thenReturn(new QuestionVineRepository.TopicOwner(9L, 4));
    when(repository.addAnswer(eq(12L), isNull(), eq(7L), anyString(), eq("回复"))).thenReturn(31L);

    service.answer(user, 12, new CreateAnswer(" 回复 ", null));

    verify(messages).notify(eq(9L), eq(7L), contains("新回答"), anyString(), eq("/question-vine?topic=12"));
  }
}
