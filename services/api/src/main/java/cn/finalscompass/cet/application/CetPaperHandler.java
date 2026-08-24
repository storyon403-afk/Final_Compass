package cn.finalscompass.cet.application;

import cn.finalscompass.cet.domain.CetPaperRepository;
import cn.finalscompass.controller.CetController.PaperInput;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CetPaperHandler {
  private static final Set<String> LEVELS=Set.of("CET4","CET6");
  private final CetPaperRepository repository;private final AuthorizationPolicy authorization;
  public CetPaperHandler(CetPaperRepository repository,AuthorizationPolicy authorization){this.repository=repository;this.authorization=authorization;}
  @Transactional public void clearSection(AuthService.CurrentUser user,long id){authorization.requireAdmin(user);repository.clearSection(id);}
  @Transactional public Map<String,Long> create(AuthService.CurrentUser user,PaperInput input){authorization.requireAdmin(user);return Map.of("id",repository.createPaper(level(input.level()),input));}
  @Transactional public void update(AuthService.CurrentUser user,long id,PaperInput input){authorization.requireAdmin(user);repository.updatePaper(id,level(input.level()),input);}
  @Transactional public void delete(AuthService.CurrentUser user,long id){authorization.requireAdmin(user);repository.deletePaper(id);}
  private String level(String value){String normalized=value==null?"":value.toUpperCase(Locale.ROOT);if(!LEVELS.contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"考试级别不正确");return normalized;}
}
