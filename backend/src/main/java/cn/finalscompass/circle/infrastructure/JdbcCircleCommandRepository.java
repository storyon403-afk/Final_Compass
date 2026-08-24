package cn.finalscompass.circle.infrastructure;

import cn.finalscompass.circle.domain.CircleCommandRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcCircleCommandRepository implements CircleCommandRepository {
  private final JdbcClient jdbc;public JdbcCircleCommandRepository(JdbcClient jdbc){this.jdbc=jdbc;}
  @Override public boolean publishedResource(String course,String teacher,long id){return jdbc.sql("""
      SELECT COUNT(*) FROM resource r JOIN course c ON c.id=r.course_id JOIN teacher t ON t.id=r.teacher_id
      WHERE r.id=:id AND c.slug=:course AND t.slug=:teacher AND r.status='PUBLISHED'
      """).param("id",id).param("course",course).param("teacher",teacher).query(Integer.class).single()==1;}
  @Override public boolean addThank(long resource,long user){int inserted=jdbc.sql(
      "INSERT IGNORE INTO resource_thank(resource_id,anonymous_user_id) VALUES (:resource,:user)")
      .param("resource",resource).param("user",user).update();if(inserted==1)jdbc.sql(
      "UPDATE resource SET thanks_count=thanks_count+1 WHERE id=:id").param("id",resource).update();return inserted==1;}
  @Override public int thanks(long id){return jdbc.sql("SELECT thanks_count FROM resource WHERE id=:id")
      .param("id",id).query(Integer.class).single();}
  @Override public long lookupCourse(String slug){return lookup("course",slug);}
  @Override public long lookupTeacher(String slug){return lookup("teacher",slug);}
  private long lookup(String table,String slug){return jdbc.sql("SELECT id FROM "+table+" WHERE slug=:slug")
      .param("slug",slug).query(Long.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"课程或老师不存在"));}
  @Override public void addResource(long teacher,long course,long user,String title,String type,String description,
      String original,String storage,String mime,long size){jdbc.sql("""
      INSERT INTO resource(teacher_id,course_id,uploader_id,title,resource_type,description,
        original_name,storage_name,mime_type,file_size,status)
      VALUES (:teacher,:course,:user,:title,:type,:description,:original,:storage,:mime,:size,'PENDING')
      """).param("teacher",teacher).param("course",course).param("user",user).param("title",title)
      .param("type",type).param("description",description).param("original",original).param("storage",storage)
      .param("mime",mime).param("size",size).update();}
  @Override public String nickname(long id){return jdbc.sql("SELECT nickname FROM anonymous_user WHERE id=:id")
      .param("id",id).query(String.class).single();}
  @Override public void addDiscussion(long teacher,long course,long user,Long parent,String content){jdbc.sql("""
      INSERT INTO discussion(teacher_id,course_id,author_id,parent_id,content,status)
      VALUES (:teacher,:course,:user,:parent,:content,'PENDING')
      """).param("teacher",teacher).param("course",course).param("user",user).param("parent",parent)
      .param("content",content).update();}
  @Override public boolean teacherBelongsToCourse(long course,long teacher){return jdbc.sql(
      "SELECT COUNT(*) FROM teacher_course WHERE course_id=:course AND teacher_id=:teacher")
      .param("course",course).param("teacher",teacher).query(Integer.class).single()>0;}
  @Override public void saveGuide(long course,long teacher,String content,String note,long editor){jdbc.sql("""
      INSERT INTO study_guide(course_id,teacher_id,content_markdown,change_note,updated_by)
      VALUES (:course,:teacher,:content,:note,:editor)
      ON DUPLICATE KEY UPDATE content_markdown=:content,change_note=:note,updated_by=:editor,updated_at=NOW()
      """).param("course",course).param("teacher",teacher).param("content",content).param("note",note)
      .param("editor",editor).update();}
  @Override public void incorporateSubmissions(List<Long> ids,long course,long teacher){jdbc.sql("""
      UPDATE guide_submission SET status='INCORPORATED',incorporated_at=NOW()
      WHERE id IN (:ids) AND course_id=:course AND teacher_id=:teacher AND status='APPROVED'
      """).param("ids",ids).param("course",course).param("teacher",teacher).update();}
  @Override public void addGuideSubmission(long course,long teacher,long author,String content){jdbc.sql("""
      INSERT INTO guide_submission(course_id,teacher_id,author_id,content_markdown,status)
      VALUES (:course,:teacher,:author,:content,'PENDING')
      """).param("course",course).param("teacher",teacher).param("author",author).param("content",content).update();}
}
