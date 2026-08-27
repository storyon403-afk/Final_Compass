package cn.finalscompass.controller;

import cn.finalscompass.message.SiteMessageService;
import cn.finalscompass.message.SiteMessageService.*;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.Authenticated;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/messages")
public class SiteMessageController {
  private final SiteMessageService messages; public SiteMessageController(SiteMessageService messages){this.messages=messages;}
  @GetMapping public List<Message> inbox(@Authenticated AuthService.CurrentUser user){return messages.inbox(user);}
  @GetMapping("/unread-count") public Map<String,Integer> unread(@Authenticated AuthService.CurrentUser user){return messages.unread(user);}
  @PutMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@Authenticated AuthService.CurrentUser user,@PathVariable long id){messages.read(user,id);}
  @PutMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT) public void readAll(@Authenticated AuthService.CurrentUser user){messages.readAll(user);}
  @PostMapping("/contact-admin") @ResponseStatus(HttpStatus.CREATED) public void contact(@Authenticated AuthService.CurrentUser user,@Valid @RequestBody ContactInput input){messages.contactAdmin(user,input);}
  @GetMapping("/admin/accounts") public List<Account> accounts(@Authenticated AuthService.CurrentUser user){return messages.accounts(user);}
  @PostMapping("/admin/send") public Map<String,Integer> send(@Authenticated AuthService.CurrentUser user,@Valid @RequestBody AdminSendInput input){return messages.adminSend(user,input);}
}
