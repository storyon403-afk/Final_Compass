package cn.finalscompass.questionvine;

import static cn.finalscompass.questionvine.QuestionVineModels.*;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.service.ActionRateLimitService;
import cn.finalscompass.message.SiteMessageService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionVineService {
  private final QuestionVineRepository repository; private final AuthorizationPolicy authorization; private final SiteMessageService messages; private final ActionRateLimitService limits;
  public QuestionVineService(QuestionVineRepository repository,AuthorizationPolicy authorization,SiteMessageService messages,ActionRateLimitService limits){this.repository=repository;this.authorization=authorization;this.messages=messages;this.limits=limits;}
  public List<Topic> topics(){return repository.topics();}
  @Transactional public Topic create(AuthService.CurrentUser user,CreateTopic input){limits.questionTopic(user.id());repository.lockTopics();int sequence=repository.nextSequence();long uid=repository.create(user.id(),alias(user.id()),input,sequence);return repository.topic(uid);}
  @Transactional public Answer answer(AuthService.CurrentUser user,long uid,CreateAnswer input){limits.questionAnswer(user.id());var topic=repository.topicOwner(uid);if(topic==null)throw missing();Long parent=input.parentAnswerId();
    if(parent!=null){var owner=repository.answerOwner(parent);if(owner==null||owner.topicId()!=uid)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"回复目标不属于这片叶子");if(owner.authorId()!=null)messages.notify(owner.authorId(),user.id(),"问题藤里有人回复了你","序号 #"+String.format("%04d",topic.sequenceNo())+" 的叶子里，你收到了一条回复。","/question-vine?topic="+uid);}
    else if(topic.authorId()!=null)messages.notify(topic.authorId(),user.id(),"你的问题收到新回答","你在问题藤发布的 #"+String.format("%04d",topic.sequenceNo())+" 收到了一条新回答。","/question-vine?topic="+uid);
    long id=repository.addAnswer(uid,parent,user.id(),alias(user.id()),input.content());return new Answer(id,parent,alias(user.id()),input.content().trim(),0,false);}
  @Transactional public DeleteResult delete(AuthService.CurrentUser user,int sequence){authorization.requireAdmin(user);repository.lockTopics();var target=repository.bySequence(sequence);if(target==null)throw missing();int answers=repository.answerCount(target.uid());repository.auditDelete(target,user.id(),answers);repository.delete(target.uid());int shifted=repository.shiftAfter(sequence);return new DeleteResult(target.uid(),sequence,shifted);}
  private String alias(long userId){String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";long value=Math.abs(userId*1103515245L+12345);StringBuilder result=new StringBuilder("匿名同学 ");for(int i=0;i<4;i++){result.append(alphabet.charAt((int)(value%alphabet.length())));value/=alphabet.length();}return result.toString();}
  private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"叶片不存在或已被摘除");}
}
