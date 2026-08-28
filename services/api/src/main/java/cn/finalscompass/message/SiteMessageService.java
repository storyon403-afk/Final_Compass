package cn.finalscompass.message;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.ActionRateLimitService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SiteMessageService {
  private final JdbcClient jdbc; private final AuthorizationPolicy authorization; private final ActionRateLimitService limits;
  public SiteMessageService(JdbcClient jdbc,AuthorizationPolicy authorization,ActionRateLimitService limits){this.jdbc=jdbc;this.authorization=authorization;this.limits=limits;}

  public List<Message> inbox(AuthService.CurrentUser user){return jdbc.sql("""
      SELECT m.id,m.kind,m.subject,m.body,m.link_path,
        CASE WHEN m.sender_user_id IS NULL THEN '系统通知'
             WHEN m.kind='USER_TO_ADMIN' THEN CONCAT(COALESCE(u.display_name,u.username),'（',u.username,'）')
             ELSE COALESCE(u.display_name,'管理员') END sender,
        m.read_at IS NOT NULL `read`,DATE_FORMAT(m.created_at,'%Y-%m-%d %H:%i') created_at
      FROM site_message m LEFT JOIN app_user u ON u.id=m.sender_user_id
      WHERE m.recipient_user_id=:user ORDER BY m.created_at DESC,m.id DESC LIMIT 200
      """).param("user",user.id()).query(Message.class).list();}
  public Map<String,Integer> unread(AuthService.CurrentUser user){return Map.of("count",jdbc.sql("SELECT COUNT(*) FROM site_message WHERE recipient_user_id=:user AND read_at IS NULL").param("user",user.id()).query(Integer.class).single());}
  public void read(AuthService.CurrentUser user,long id){jdbc.sql("UPDATE site_message SET read_at=COALESCE(read_at,NOW()) WHERE id=:id AND recipient_user_id=:user").param("id",id).param("user",user.id()).update();}
  public void readAll(AuthService.CurrentUser user){jdbc.sql("UPDATE site_message SET read_at=NOW() WHERE recipient_user_id=:user AND read_at IS NULL").param("user",user.id()).update();}
  @Transactional public void contactAdmin(AuthService.CurrentUser user,ContactInput input){limits.contactAdmin(user.id());int count=jdbc.sql("""
      INSERT INTO site_message(recipient_user_id,sender_user_id,kind,subject,body)
      SELECT id,:sender,'USER_TO_ADMIN',:subject,:body FROM app_user WHERE role='ADMIN' AND active=TRUE
      """).param("sender",user.id()).param("subject",input.subject().trim()).param("body",input.body().trim()).update();if(count==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"当前没有可联系的管理员");}
  public List<Account> accounts(AuthService.CurrentUser user){authorization.requireAdmin(user);return jdbc.sql("SELECT username,display_name,role FROM app_user WHERE active=TRUE ORDER BY role,username").query(Account.class).list();}
  @Transactional public Map<String,Integer> adminSend(AuthService.CurrentUser user,AdminSendInput input){authorization.requireAdmin(user);String target=input.targetUsername()==null?"":input.targetUsername().trim();int count;
    if(target.isEmpty()){limits.adminBroadcast(user.id());count=jdbc.sql("""
        INSERT INTO site_message(recipient_user_id,sender_user_id,kind,subject,body,link_path)
        SELECT id,:sender,'ADMIN_BROADCAST',:subject,:body,:link FROM app_user WHERE active=TRUE
        """).param("sender",user.id()).param("subject",input.subject().trim()).param("body",input.body().trim()).param("link",cleanLink(input.linkPath())).update();}
    else count=jdbc.sql("""
        INSERT INTO site_message(recipient_user_id,sender_user_id,kind,subject,body,link_path)
        SELECT id,:sender,'ADMIN_DIRECT',:subject,:body,:link FROM app_user WHERE username=:target AND active=TRUE
        """).param("sender",user.id()).param("subject",input.subject().trim()).param("body",input.body().trim()).param("link",cleanLink(input.linkPath())).param("target",target).update();
    if(count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"目标账号不存在或已停用");return Map.of("sent",count);}
  public void notify(long recipient,long actor,String subject,String body,String link){if(recipient<=0||recipient==actor)return;jdbc.sql("""
      INSERT INTO site_message(recipient_user_id,kind,subject,body,link_path) VALUES (:recipient,'SYSTEM',:subject,:body,:link)
      """).param("recipient",recipient).param("subject",subject).param("body",body).param("link",link).update();}
  private String cleanLink(String link){if(link==null||link.isBlank())return null;String value=link.trim();if(!value.startsWith("/")||value.startsWith("//"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"站内链接格式不正确");return value;}

  public record Message(long id,String kind,String subject,String body,String linkPath,String sender,boolean read,String createdAt){}
  public record Account(String username,String displayName,String role){}
  public record ContactInput(@NotBlank @Size(max=120) String subject,@NotBlank @Size(max=4000) String body){}
  public record AdminSendInput(@Size(max=40) String targetUsername,@NotBlank @Size(max=120) String subject,@NotBlank @Size(max=8000) String body,@Size(max=500) String linkPath){}
}
