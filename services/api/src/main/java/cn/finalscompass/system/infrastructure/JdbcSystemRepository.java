package cn.finalscompass.system.infrastructure;

import cn.finalscompass.model.ApiModels.Announcement;
import cn.finalscompass.system.domain.SystemRepository;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSystemRepository implements SystemRepository {
  private final JdbcClient jdbc;
  public JdbcSystemRepository(JdbcClient jdbc){this.jdbc=jdbc;}
  @Override public void checkHealth(){jdbc.sql("SELECT 1").query(Integer.class).single();}
  @Override public Announcement announcement(){return jdbc.sql(
      "SELECT content,enabled,updated_at FROM system_announcement WHERE id=1")
      .query(Announcement.class).single();}
  @Override public void updateAnnouncement(String content,boolean enabled,long adminId){
    jdbc.sql("UPDATE system_announcement SET content=:content,enabled=:enabled,updated_by=:admin,updated_at=NOW() WHERE id=1")
        .param("content",content).param("enabled",enabled).param("admin",adminId).update();
  }
  @Override public Map<String,Object> metrics(){return jdbc.sql("""
      SELECT (SELECT COUNT(*) FROM course WHERE active=TRUE) courses,
        (SELECT COUNT(*) FROM teacher) teachers,
        (SELECT COUNT(*) FROM resource WHERE status='PUBLISHED') published_resources,
        (SELECT COUNT(*) FROM resource WHERE status='PENDING') pending_resources,
        (SELECT COUNT(*) FROM discussion WHERE status='VISIBLE') visible_discussions,
        (SELECT COUNT(*) FROM discussion WHERE status='PENDING') pending_discussions
      """).query().singleRow();}
  @Override public List<Map<String,Object>> pendingModeration(){return jdbc.sql("""
      SELECT 'RESOURCE' item_type,id,title summary,created_at FROM resource WHERE status='PENDING'
      UNION ALL SELECT 'DISCUSSION',id,LEFT(content,120),created_at FROM discussion WHERE status='PENDING'
      UNION ALL SELECT 'GUIDE_SUBMISSION',id,LEFT(content_markdown,120),created_at FROM guide_submission WHERE status='PENDING'
      ORDER BY created_at
      """).query().listOfRows();}
  @Override public void expireBetaRequests(){jdbc.sql("UPDATE beta_access_request SET status='EXPIRED' WHERE status IN ('CREATED','CODE_SENT') AND expires_at<=NOW()").update();}
  @Override public List<Map<String,Object>> betaRequests(){return jdbc.sql("""
      SELECT r.id,r.email,r.phone,r.status,r.failed_attempts,r.expires_at,r.verified_at,r.created_at,
        r.reviewed_at,r.rejection_reason,a.reserved_username suggested_username
      FROM beta_access_request r LEFT JOIN account_reservation a ON a.request_id=r.id
      ORDER BY r.created_at DESC LIMIT 200
      """).query().listOfRows();}
  @Override public Long contributionOwner(String type,long id){
    String sql=switch(type){
      case "RESOURCE"->"SELECT u.app_user_id FROM resource x JOIN anonymous_user u ON u.id=x.uploader_id WHERE x.id=:id";
      case "DISCUSSION"->"SELECT u.app_user_id FROM discussion x JOIN anonymous_user u ON u.id=x.author_id WHERE x.id=:id";
      case "GUIDE_SUBMISSION"->"SELECT u.app_user_id FROM guide_submission x JOIN anonymous_user u ON u.id=x.author_id WHERE x.id=:id";
      default->throw new IllegalArgumentException("不支持的审核类型");};
    return jdbc.sql(sql).param("id",id).query(Long.class).optional().orElse(null);
  }
  @Override public int updateModerationStatus(String type,long id,String status){
    String table=switch(type){case "RESOURCE"->"resource";case "DISCUSSION"->"discussion";case "GUIDE_SUBMISSION"->"guide_submission";default->throw new IllegalArgumentException("不支持的审核类型");};
    return jdbc.sql("UPDATE "+table+" SET status=:status WHERE id=:id AND status='PENDING'")
        .param("status",status).param("id",id).update();
  }
  @Override public void recordModeration(String type,long id,String decision,long reviewerId){
    jdbc.sql("INSERT INTO moderation_audit(item_type,item_id,decision,reviewer_id) VALUES (:type,:id,:decision,:reviewer)")
        .param("type",type).param("id",id).param("decision",decision).param("reviewer",reviewerId).update();
  }
  @Override public void markGuideReviewed(long id){jdbc.sql("UPDATE guide_submission SET reviewed_at=NOW() WHERE id=:id").param("id",id).update();}
  @Override public int removeDiscussion(long id){return jdbc.sql("UPDATE discussion SET status='REMOVED' WHERE id=:id AND status='VISIBLE'").param("id",id).update();}
}
