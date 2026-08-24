package cn.finalscompass.circle.application;

import cn.finalscompass.circle.domain.CircleQueryRepository;
import cn.finalscompass.model.ApiModels.*;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import cn.finalscompass.shared.storage.UploadStorage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CircleQueryHandler {
  private final CircleQueryRepository repository;private final UploadStorage storage;private final AuthorizationPolicy authorization;
  public CircleQueryHandler(CircleQueryRepository repository,UploadStorage storage,AuthorizationPolicy authorization){this.repository=repository;this.storage=storage;this.authorization=authorization;}
  public List<Resource> resources(String course,String teacher){return repository.resources(course,teacher);}
  public ResponseEntity<org.springframework.core.io.Resource> file(String course,String teacher,long id,String disposition){
    var stored=repository.resourceFile(course,teacher,id);if(!storage.exists(stored.storageName()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料文件不存在");
    MediaType type;try{type=MediaType.parseMediaType(stored.mimeType()==null?"application/octet-stream":stored.mimeType());}catch(IllegalArgumentException ignored){type=MediaType.APPLICATION_OCTET_STREAM;}
    var content="attachment".equalsIgnoreCase(disposition)?ContentDisposition.attachment():ContentDisposition.inline();repository.incrementDownloads(id);
    return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,
        content.filename(stored.originalName(),StandardCharsets.UTF_8).build().toString())
        .body(new org.springframework.core.io.FileSystemResource(storage.resolve(stored.storageName())));
  }
  public List<Discussion> discussions(String course,String teacher,LocalDate date){return repository.discussions(course,teacher,date);}
  public CircleSummary summary(String course,String teacher){return repository.summary(course,teacher);}
  public StudyGuide guide(String course,String teacher){return repository.guide(course,teacher);}
  public List<GuideSubmission> approved(AuthService.CurrentUser user,String course,String teacher){authorization.requireAdmin(user);return repository.approvedSubmissions(course,teacher);}
}
