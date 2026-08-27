package cn.finalscompass.controller;

import static cn.finalscompass.questionvine.QuestionVineModels.*;
import cn.finalscompass.questionvine.QuestionVineService;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.Authenticated;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/question-vine")
public class QuestionVineController {
  private final QuestionVineService service;
  public QuestionVineController(QuestionVineService service){this.service=service;}
  @GetMapping("/topics") public List<Topic> topics(){return service.topics();}
  @PostMapping("/topics") @ResponseStatus(HttpStatus.CREATED) public Topic create(@Authenticated AuthService.CurrentUser user,@Valid @RequestBody CreateTopic input){return service.create(user,input);}
  @PostMapping("/topics/{uid}/answers") @ResponseStatus(HttpStatus.CREATED) public Answer answer(@Authenticated AuthService.CurrentUser user,@PathVariable long uid,@Valid @RequestBody CreateAnswer input){return service.answer(user,uid,input);}
  @DeleteMapping("/admin/topics/{sequence}") public DeleteResult delete(@Authenticated AuthService.CurrentUser user,@PathVariable int sequence){return service.delete(user,sequence);}
}
