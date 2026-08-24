package cn.finalscompass.circle.application;

import cn.finalscompass.circle.domain.CircleCommandRepository;
import cn.finalscompass.model.ApiModels.*;
import cn.finalscompass.service.*;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import cn.finalscompass.shared.storage.UploadStorage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CircleCommandHandler {
  private static final Set<String> EXTENSIONS=Set.of("pdf","doc","docx","ppt","pptx","zip","png","jpg","jpeg");
  private final CircleCommandRepository repository;private final CircleQueryHandler queries;
  private final AnonymousIdentityService identities;private final ActivityService activity;
  private final UploadStorage storage;private final AuthorizationPolicy authorization;
  public CircleCommandHandler(CircleCommandRepository repository,CircleQueryHandler queries,
      AnonymousIdentityService identities,ActivityService activity,UploadStorage storage,AuthorizationPolicy authorization){
    this.repository=repository;this.queries=queries;this.identities=identities;this.activity=activity;this.storage=storage;this.authorization=authorization;}
  @Transactional public Map<String,Object> thank(AuthService.CurrentUser user,String course,String teacher,long id){
    long anonymous=identities.internalIdForAccount(user.id());if(!repository.publishedResource(course,teacher,id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"资料不存在或尚未公开");
    boolean added=repository.addThank(id,anonymous);return Map.of("added",added,"thanks",repository.thanks(id));
  }
  @Transactional public void upload(AuthService.CurrentUser user,String course,String teacher,String title,String type,
      String description,MultipartFile file)throws IOException{
    if(file.isEmpty()||file.getSize()>20L*1024*1024)throw bad("文件为空或超过 20MB");
    String original=StringUtils.cleanPath(file.getOriginalFilename()==null?"resource":file.getOriginalFilename());
    String ext=StringUtils.getFilenameExtension(original);if(ext==null||!EXTENSIONS.contains(ext.toLowerCase()))throw bad("不支持此文件类型");
    long anonymous=identities.internalIdForAccount(user.id()),courseId=repository.lookupCourse(course),teacherId=repository.lookupTeacher(teacher);
    String name=UUID.randomUUID()+"."+ext.toLowerCase();storage.store(name,file);
    try{repository.addResource(teacherId,courseId,anonymous,title,type,description,original,name,file.getContentType(),file.getSize());activity.recordResourceSubmitted(user.id(),name);}
    catch(RuntimeException error){storage.deleteQuietly(name);throw error;}
  }
  @Transactional public Discussion discuss(AuthService.CurrentUser user,String course,String teacher,CreateDiscussion input){
    long anonymous=identities.internalIdForAccount(user.id()),courseId=repository.lookupCourse(course),teacherId=repository.lookupTeacher(teacher);
    repository.addDiscussion(teacherId,courseId,anonymous,input.parentId(),input.content());
    return new Discussion(0,repository.nickname(anonymous),input.content(),0,0,input.parentId(),LocalDateTime.now());
  }
  @Transactional public StudyGuide updateGuide(AuthService.CurrentUser user,String course,String teacher,UpdateStudyGuide input){
    authorization.requireAdmin(user);long courseId=repository.lookupCourse(course),teacherId=repository.lookupTeacher(teacher);membership(courseId,teacherId);
    String content=input.contentMarkdown()==null?"":input.contentMarkdown().trim(),note=input.changeNote()==null?"":input.changeNote().trim();
    repository.saveGuide(courseId,teacherId,content,note,user.id());if(input.incorporatedSubmissionIds()!=null&&!input.incorporatedSubmissionIds().isEmpty())repository.incorporateSubmissions(input.incorporatedSubmissionIds(),courseId,teacherId);
    return queries.guide(course,teacher);
  }
  @Transactional public Map<String,String> submitGuide(AuthService.CurrentUser user,String course,String teacher,CreateGuideSubmission input){
    long author=identities.internalIdForAccount(user.id()),courseId=repository.lookupCourse(course),teacherId=repository.lookupTeacher(teacher);membership(courseId,teacherId);
    repository.addGuideSubmission(courseId,teacherId,author,input.contentMarkdown().trim());return Map.of("status","PENDING");
  }
  private void membership(long course,long teacher){if(!repository.teacherBelongsToCourse(course,teacher))throw bad("老师不属于该课程");}
  private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
