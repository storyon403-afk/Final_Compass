package cn.finalscompass.circle.infrastructure;

import cn.finalscompass.circle.domain.CircleQueryRepository;
import cn.finalscompass.model.ApiModels.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcCircleQueryRepository implements CircleQueryRepository {
  private final JdbcClient jdbc;public JdbcCircleQueryRepository(JdbcClient jdbc){this.jdbc=jdbc;}
  @Override public List<Resource> resources(String course,String teacher){return jdbc.sql("""
      SELECT r.id,r.title,r.resource_type type,r.description,r.original_name,r.file_size,
        r.download_count downloads,r.thanks_count thanks,u.nickname contributor,r.created_at
      FROM resource r JOIN anonymous_user u ON u.id=r.uploader_id JOIN course c ON c.id=r.course_id
      JOIN teacher t ON t.id=r.teacher_id WHERE c.slug=:course AND t.slug=:teacher AND r.status='PUBLISHED'
      ORDER BY r.created_at DESC
      """).param("course",course).param("teacher",teacher).query(Resource.class).list();}
  @Override public StoredFile resourceFile(String course,String teacher,long id){return jdbc.sql("""
      SELECT r.storage_name,r.original_name,r.mime_type FROM resource r
      JOIN course c ON c.id=r.course_id JOIN teacher t ON t.id=r.teacher_id
      WHERE r.id=:id AND c.slug=:course AND t.slug=:teacher AND r.status='PUBLISHED'
      """).param("id",id).param("course",course).param("teacher",teacher).query(StoredFile.class)
      .optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"资料不存在或尚未公开"));}
  @Override public void incrementDownloads(long id){jdbc.sql("UPDATE resource SET download_count=download_count+1 WHERE id=:id").param("id",id).update();}
  @Override public List<Discussion> discussions(String course,String teacher,LocalDate date){
    String filter=date==null?"":"AND DATE(d.created_at)=:date";var query=jdbc.sql("""
        SELECT d.id,u.nickname author,d.content,d.like_count likes,
          (SELECT COUNT(*) FROM discussion child WHERE child.parent_id=d.id AND child.status='VISIBLE') replies,
          d.parent_id,d.created_at FROM discussion d JOIN anonymous_user u ON u.id=d.author_id
        JOIN course c ON c.id=d.course_id JOIN teacher t ON t.id=d.teacher_id
        WHERE c.slug=:course AND t.slug=:teacher AND d.status='VISIBLE' AND d.parent_id IS NULL %s
        ORDER BY d.created_at DESC
        """.formatted(filter)).param("course",course).param("teacher",teacher);if(date!=null)query=query.param("date",date);
    return query.query(Discussion.class).list();
  }
  @Override public CircleSummary summary(String course,String teacher){return jdbc.sql("""
      SELECT COUNT(DISTINCT r.id) resources,COUNT(DISTINCT d.id) discussions,
        COUNT(DISTINCT COALESCE(r.uploader_id,d.author_id)) contributors
      FROM course c JOIN teacher_course tc ON tc.course_id=c.id JOIN teacher t ON t.id=tc.teacher_id
      LEFT JOIN resource r ON r.course_id=c.id AND r.teacher_id=t.id AND r.status='PUBLISHED'
      LEFT JOIN discussion d ON d.course_id=c.id AND d.teacher_id=t.id AND d.status='VISIBLE'
      WHERE c.slug=:course AND t.slug=:teacher
      """).param("course",course).param("teacher",teacher).query(CircleSummary.class).single();}
  @Override public StudyGuide guide(String course,String teacher){return jdbc.sql("""
      SELECT g.content_markdown,g.change_note,g.updated_at FROM study_guide g
      JOIN course c ON c.id=g.course_id JOIN teacher t ON t.id=g.teacher_id
      WHERE c.slug=:course AND t.slug=:teacher
      """).param("course",course).param("teacher",teacher).query(StudyGuide.class).optional()
      .orElse(new StudyGuide("","",null));}
  @Override public List<GuideSubmission> approvedSubmissions(String course,String teacher){return jdbc.sql("""
      SELECT s.id,s.content_markdown,u.nickname author,s.status,s.created_at FROM guide_submission s
      JOIN anonymous_user u ON u.id=s.author_id JOIN course c ON c.id=s.course_id JOIN teacher t ON t.id=s.teacher_id
      WHERE c.slug=:course AND t.slug=:teacher AND s.status='APPROVED' ORDER BY s.created_at
      """).param("course",course).param("teacher",teacher).query(GuideSubmission.class).list();}
}
