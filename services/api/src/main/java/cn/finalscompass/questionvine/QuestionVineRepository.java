package cn.finalscompass.questionvine;

import static cn.finalscompass.questionvine.QuestionVineModels.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionVineRepository {
  private final JdbcClient jdbc; private final ObjectMapper json;
  public QuestionVineRepository(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

  public List<Topic> topics(){return jdbc.sql("""
      SELECT id uid,sequence_no id,title,category,tags_json,DATE_FORMAT(created_at,'%Y.%m.%d') date,
        body,status,anonymous_name author FROM question_vine_topic ORDER BY sequence_no DESC
      """).query(TopicRow.class).list().stream().map(row->new Topic(row.uid(),row.id(),row.title(),row.category(),tags(row.tagsJson()),row.date(),row.body(),row.status(),row.author(),answers(row.uid()))).toList();}
  public Topic topic(long uid){return topics().stream().filter(item->item.uid()==uid).findFirst().orElse(null);}
  public void lockTopics(){jdbc.sql("SELECT id FROM question_vine_topic ORDER BY sequence_no FOR UPDATE").query(Long.class).list();}
  public int nextSequence(){return jdbc.sql("SELECT COALESCE(MAX(sequence_no),0)+1 FROM question_vine_topic").query(Integer.class).single();}
  public long create(long userId,String alias,CreateTopic input,int sequence){jdbc.sql("""
      INSERT INTO question_vine_topic(sequence_no,author_id,anonymous_name,title,category,tags_json,body)
      VALUES (:sequence,:user,:alias,:title,:category,:tags,:body)
      """).param("sequence",sequence).param("user",userId).param("alias",alias).param("title",input.title().trim())
      .param("category",input.category().trim()).param("tags",writeTags(input.tags())).param("body",input.body().trim()).update();
    return jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();}
  public long addAnswer(long topicId,Long parentId,long userId,String alias,String content){jdbc.sql("""
      INSERT INTO question_vine_answer(topic_id,parent_answer_id,author_id,anonymous_name,content) VALUES (:topic,:parent,:user,:alias,:content)
      """).param("topic",topicId).param("parent",parentId).param("user",userId).param("alias",alias).param("content",content.trim()).update();
    return jdbc.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();}
  public boolean exists(long uid){return jdbc.sql("SELECT COUNT(*) FROM question_vine_topic WHERE id=:id").param("id",uid).query(Integer.class).single()>0;}
  public TopicOwner topicOwner(long uid){return jdbc.sql("SELECT author_id,sequence_no FROM question_vine_topic WHERE id=:id").param("id",uid).query(TopicOwner.class).optional().orElse(null);}
  public AnswerOwner answerOwner(long id){return jdbc.sql("SELECT author_id,topic_id FROM question_vine_answer WHERE id=:id").param("id",id).query(AnswerOwner.class).optional().orElse(null);}
  public TopicRow bySequence(int sequence){return jdbc.sql("""
      SELECT id uid,sequence_no id,title,category,tags_json,DATE_FORMAT(created_at,'%Y.%m.%d') date,
        body,status,anonymous_name author FROM question_vine_topic WHERE sequence_no=:sequence
      """).param("sequence",sequence).query(TopicRow.class).optional().orElse(null);}
  public int answerCount(long uid){return jdbc.sql("SELECT COUNT(*) FROM question_vine_answer WHERE topic_id=:id").param("id",uid).query(Integer.class).single();}
  public void auditDelete(TopicRow row,long adminId,int answerCount){jdbc.sql("""
      INSERT INTO question_vine_moderation_audit(deleted_topic_id,deleted_sequence_no,title_snapshot,category_snapshot,admin_id,answer_count)
      VALUES (:id,:sequence,:title,:category,:admin,:answers)
      """).param("id",row.uid()).param("sequence",row.id()).param("title",row.title()).param("category",row.category()).param("admin",adminId).param("answers",answerCount).update();}
  public void delete(long uid){jdbc.sql("DELETE FROM question_vine_topic WHERE id=:id").param("id",uid).update();}
  public int shiftAfter(int sequence){return jdbc.sql("UPDATE question_vine_topic SET sequence_no=sequence_no-1 WHERE sequence_no>:sequence ORDER BY sequence_no ASC").param("sequence",sequence).update();}
  private List<Answer> answers(long topicId){return jdbc.sql("""
      SELECT id,parent_answer_id parent_id,anonymous_name author,content,helpful_count helpful,accepted FROM question_vine_answer
      WHERE topic_id=:topic ORDER BY created_at,id
      """).param("topic",topicId).query(Answer.class).list();}
  private List<String> tags(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception ignored){return List.of();}}
  private String writeTags(List<String> value){try{return json.writeValueAsString(value==null?List.of():value);}catch(Exception error){throw new IllegalArgumentException("标签格式不正确",error);}}
  public record TopicRow(long uid,int id,String title,String category,String tagsJson,String date,String body,String status,String author){}
  public record TopicOwner(Long authorId,int sequenceNo){}
  public record AnswerOwner(Long authorId,long topicId){}
}
