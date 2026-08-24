package cn.finalscompass.cet.application;

import cn.finalscompass.cet.domain.CetQueryRepository;
import cn.finalscompass.controller.CetController.CetItem;
import cn.finalscompass.controller.CetController.CetPaper;
import cn.finalscompass.controller.CetController.SectionResource;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CetQueryHandler {
  private static final Set<String> LEVELS=Set.of("CET4","CET6");
  private static final Set<String> MODES=Set.of("PRACTICE","INTENSIVE");
  private final CetQueryRepository repository;private final AuthorizationPolicy authorization;
  public CetQueryHandler(CetQueryRepository repository,AuthorizationPolicy authorization){this.repository=repository;this.authorization=authorization;}
  public List<CetPaper> papers(String level){String value=level==null||level.isBlank()?null:level(level);return repository.findPublishedPapers(value);}
  public List<CetItem> items(String level,String mode,String section){return repository.findPublishedItems(level(level),mode(mode),blankToNull(section));}
  public List<CetItem> adminItems(AuthService.CurrentUser user){authorization.requireAdmin(user);return repository.findAllItems();}
  public List<SectionResource> sections(AuthService.CurrentUser user){authorization.requireAdmin(user);return repository.findSections();}
  private String level(String value){String normalized=value==null?"":value.toUpperCase(Locale.ROOT);if(!LEVELS.contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"考试级别不正确");return normalized;}
  private String mode(String value){String normalized=value==null?"":value.toUpperCase(Locale.ROOT);if(!MODES.contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"练习模式不正确");return normalized;}
  private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
