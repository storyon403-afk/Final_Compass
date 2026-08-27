package cn.finalscompass.questionvine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class QuestionVineModels {
  private QuestionVineModels() {}
  public record Topic(long uid,int id,String title,String category,List<String> tags,String date,String body,String status,String author,List<Answer> answers) {}
  public record Answer(long id,Long parentId,String author,String content,int helpful,boolean accepted) {}
  public record CreateTopic(@NotBlank @Size(max=100) String title,@NotBlank @Size(max=30) String category,@Size(max=3) List<@Size(max=20) String> tags,@NotBlank @Size(max=5000) String body) {}
  public record CreateAnswer(@NotBlank @Size(max=2000) String content,Long parentAnswerId) {}
  public record DeleteResult(long deletedUid,int deletedSequence,int shiftedCount) {}
}
