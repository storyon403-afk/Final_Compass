package cn.finalscompass.survey.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 问卷模块稳定的 HTTP 数据模型 */
public final class SurveyModels {
  private SurveyModels() {}

  public record Answer(
      long questionId, @Min(1) @Max(5) int rating, @Size(max = 1000) String suggestion) {}

  public record Submission(
      List<@Valid Answer> answers, @Size(max = 2000) String overallSuggestion) {}

  public record QuestionUpdate(List<@NotBlank @Size(max = 300) String> questions) {}
}
