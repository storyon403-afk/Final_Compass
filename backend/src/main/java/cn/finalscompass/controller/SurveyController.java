package cn.finalscompass.controller;

import cn.finalscompass.survey.api.SurveyModels.QuestionUpdate;
import cn.finalscompass.survey.api.SurveyModels.Submission;
import cn.finalscompass.survey.application.SurveyHandler;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.Authenticated;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 问卷用例的 HTTP 适配器 */
@Validated
@RestController
@RequestMapping("/api/survey")
public class SurveyController {
  private final SurveyHandler handler;
  public SurveyController(SurveyHandler handler){this.handler=handler;}

  @GetMapping public List<Map<String,Object>> questions(){
    return handler.questions();
  }
  @PostMapping("/submissions") public Map<String,Object> submit(
      @Authenticated AuthService.CurrentUser user,@Valid @RequestBody Submission body){
    return handler.submit(user,body);
  }
  @GetMapping("/admin") public Map<String,Object> adminOverview(@Authenticated AuthService.CurrentUser user){
    return handler.adminOverview(user);
  }
  @PutMapping("/admin/questions") public Map<String,String> updateQuestions(
      @Authenticated AuthService.CurrentUser user,@Valid @RequestBody QuestionUpdate body){
    return handler.updateQuestions(user,body);
  }
}
