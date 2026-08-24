package cn.finalscompass.survey.application;

import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import cn.finalscompass.survey.api.SurveyModels.*;
import cn.finalscompass.survey.domain.SurveyRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyHandler {
  private final SurveyRepository repository;
  private final AuthorizationPolicy authorization;
  public SurveyHandler(SurveyRepository repository,AuthorizationPolicy authorization){
    this.repository=repository;this.authorization=authorization;
  }

  public List<Map<String,Object>> questions(){
    return repository.findActiveQuestionViews();
  }
  @Transactional public Map<String,Object> submit(AuthService.CurrentUser user,Submission body){
    if(user.isAdmin()) throw new IllegalArgumentException("管理员无需提交用户问卷");
    if(body.answers()==null||body.answers().isEmpty()) throw new IllegalArgumentException("请完成问卷后再提交");
    var active=repository.findActiveQuestions();
    if(body.answers().size()!=active.size()) throw new IllegalArgumentException("问卷内容已更新，请刷新后重新填写");
    long id=repository.createSubmission(user.id(),clean(body.overallSuggestion()));
    for(var question:active){
      Answer answer=body.answers().stream().filter(item->item.questionId()==question.id()).findFirst()
          .orElseThrow(()->new IllegalArgumentException("请回答全部问题"));
      repository.addAnswer(id,question,answer.rating(),clean(answer.suggestion()));
    }
    return Map.of("id",id,"message","感谢你的真实反馈，我们会认真阅读每一条建议");
  }
  public Map<String,Object> adminOverview(AuthService.CurrentUser user){
    authorization.requireAdmin(user);
    var result=new ArrayList<Map<String,Object>>();
    for(var submission:repository.findRecentSubmissions()){
      var item=new LinkedHashMap<String,Object>(submission);
      item.put("answers",repository.findAnswers(((Number)submission.get("id")).longValue()));
      result.add(item);
    }
    return Map.of("questions",repository.findAllQuestionViews(),"submissions",result);
  }
  @Transactional public Map<String,String> updateQuestions(AuthService.CurrentUser user,QuestionUpdate body){
    authorization.requireAdmin(user);
    if(body.questions()==null||body.questions().isEmpty()) throw new IllegalArgumentException("问卷至少需要一道题");
    if(body.questions().size()>12) throw new IllegalArgumentException("问卷最多设置 12 道题");
    repository.deactivateQuestions(); int order=10;
    for(String prompt:body.questions()){
      String value=prompt==null?"":prompt.trim();
      if(value.isBlank()||value.length()>300) throw new IllegalArgumentException("题目不能为空且最多 300 字");
      repository.addQuestion(value,order); order+=10;
    }
    return Map.of("message","问卷内容已更新");
  }
  private String clean(String value){return value==null||value.isBlank()?null:value.trim();}
}
