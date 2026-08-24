package cn.finalscompass.system.application;

import cn.finalscompass.model.ApiModels.Announcement;
import cn.finalscompass.model.ApiModels.UpdateAnnouncement;
import cn.finalscompass.service.AccountAllocationService;
import cn.finalscompass.service.ActivityService;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import cn.finalscompass.system.domain.SystemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemHandler {
  private final SystemRepository repository; private final AuthorizationPolicy authorization;
  private final ActivityService activity; private final AccountAllocationService accounts;
  public SystemHandler(SystemRepository repository,AuthorizationPolicy authorization,
      ActivityService activity,AccountAllocationService accounts){
    this.repository=repository;this.authorization=authorization;this.activity=activity;this.accounts=accounts;
  }
  public Map<String,Object> health(){repository.checkHealth();return Map.of("status","ok","time",Instant.now().toString());}
  public Announcement announcement(){return repository.announcement();}
  public Announcement updateAnnouncement(AuthService.CurrentUser user,UpdateAnnouncement input){
    authorization.requireAdmin(user);repository.updateAnnouncement(input.content().trim(),input.enabled(),user.id());return repository.announcement();
  }
  public Map<String,Object> metrics(AuthService.CurrentUser user){authorization.requireAdmin(user);return repository.metrics();}
  public List<Map<String,Object>> moderation(AuthService.CurrentUser user){authorization.requireAdmin(user);return repository.pendingModeration();}
  public List<Map<String,Object>> betaRequests(AuthService.CurrentUser user){authorization.requireAdmin(user);repository.expireBetaRequests();accounts.ensureVerifiedReservations();return repository.betaRequests();}
  @Transactional public Map<String,String> moderate(AuthService.CurrentUser user,String type,long id,String decision){
    authorization.requireAdmin(user);String item=type.toUpperCase(),action=decision.toUpperCase();
    if(!List.of("RESOURCE","DISCUSSION","GUIDE_SUBMISSION").contains(item))throw new IllegalArgumentException("不支持的审核类型");
    if(!List.of("APPROVE","REJECT").contains(action))throw new IllegalArgumentException("decision 只能是 APPROVE 或 REJECT");
    String status=switch(item){case "RESOURCE"->action.equals("APPROVE")?"PUBLISHED":"REJECTED";case "DISCUSSION"->action.equals("APPROVE")?"VISIBLE":"REMOVED";default->action.equals("APPROVE")?"APPROVED":"REJECTED";};
    Long owner=action.equals("APPROVE")?repository.contributionOwner(item,id):null;
    if(repository.updateModerationStatus(item,id,status)!=1)throw new IllegalArgumentException("待审核记录不存在或已处理");
    repository.recordModeration(item,id,action,user.id());if(item.equals("GUIDE_SUBMISSION"))repository.markGuideReviewed(id);
    if(owner!=null)activity.recordApproved(owner,item,id);return Map.of("status",status);
  }
  @Transactional public Map<String,String> removeDiscussion(AuthService.CurrentUser user,long id){
    authorization.requireAdmin(user);if(repository.removeDiscussion(id)!=1)throw new IllegalArgumentException("帖子不存在或已删除");
    repository.recordModeration("DISCUSSION",id,"REJECT",user.id());return Map.of("status","REMOVED");
  }
}
