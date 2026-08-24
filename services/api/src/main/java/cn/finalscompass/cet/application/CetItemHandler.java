package cn.finalscompass.cet.application;

import cn.finalscompass.cet.domain.CetItemRepository;
import cn.finalscompass.controller.CetController.ItemInput;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import cn.finalscompass.shared.storage.UploadStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CetItemHandler {
  private static final Set<String> MODES=Set.of("PRACTICE","INTENSIVE");
  private static final Set<String> ANSWERS=Set.of("CHOICE","TEXT");
  private static final Set<String> AUDIO_EXTENSIONS=Set.of("mp3","m4a","wav","ogg","webm","aac");
  private static final Set<String> PRACTICE=Set.of("WRITING","LISTENING_PASSAGE","WORD_BANK","MATCHING","CAREFUL_READING","TRANSLATION");
  private static final Set<String> INTENSIVE=Set.of("NEWS","LONG_CONVERSATION","LISTENING_PASSAGE","LECTURE");
  private final CetItemRepository repository;private final UploadStorage storage;private final AuthorizationPolicy authorization;
  public CetItemHandler(CetItemRepository repository,UploadStorage storage,AuthorizationPolicy authorization){this.repository=repository;this.storage=storage;this.authorization=authorization;}
  @Transactional public Map<String,Long> create(AuthService.CurrentUser user,ItemInput input){authorization.requireAdmin(user);validate(input);return Map.of("id",repository.createItem(input));}
  @Transactional public void update(AuthService.CurrentUser user,long id,ItemInput input){
    authorization.requireAdmin(user);validate(input);var original=repository.itemIdentity(id);
    if(original.paperId()!=input.paperId()||!original.mode().equals(input.mode())||!original.section().equals(input.section()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"已有题目的套卷、训练方式和题型不可修改");
    repository.updateItem(id,input);
  }
  @Transactional public void uploadPracticeAudio(AuthService.CurrentUser user,long id,MultipartFile file)throws IOException{
    authorization.requireAdmin(user);validateAudio(file);if(!repository.paperExists(id))throw notFound();
    String original=cleanName(file),ext=StringUtils.getFilenameExtension(original).toLowerCase(Locale.ROOT);
    String name="cet-paper-"+UUID.randomUUID()+"."+ext;storage.store(name,file);
    try{String previous=repository.replacePracticeAudio(id,name,original,file.getContentType());if(previous!=null&&!previous.equals(name))storage.deleteQuietly(previous);}
    catch(RuntimeException error){storage.deleteQuietly(name);throw error;}
  }
  @Transactional public void uploadItemAudio(AuthService.CurrentUser user,long id,MultipartFile file)throws IOException{
    authorization.requireAdmin(user);validateAudio(file);if(!repository.itemExists(id))throw notFound();
    String original=cleanName(file),ext=StringUtils.getFilenameExtension(original).toLowerCase(Locale.ROOT);
    String name="cet-"+UUID.randomUUID()+"."+ext;storage.store(name,file);
    try{String previous=repository.replaceItemAudio(id,name,original,file.getContentType());if(previous!=null&&!previous.equals(name))storage.deleteQuietly(previous);}
    catch(RuntimeException error){storage.deleteQuietly(name);throw error;}
  }
  public ResponseEntity<org.springframework.core.io.Resource> audio(long id)throws IOException{
    var audio=repository.audio(id);if(audio.storageName()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"该题尚未上传音频");
    Path path=storage.resolve(audio.storageName());if(!storage.exists(audio.storageName()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"音频文件不存在");
    String detected=audio.mimeType();if(detected==null){try(InputStream input=Files.newInputStream(path)){byte[] header=input.readNBytes(12);detected=header.length>=8&&header[4]=='f'&&header[5]=='t'&&header[6]=='y'&&header[7]=='p'?"audio/mp4":Files.probeContentType(path);}catch(IOException ignored){detected=null;}}
    MediaType type;try{type=MediaType.parseMediaType(detected==null?"audio/mpeg":detected);}catch(IllegalArgumentException ignored){type=MediaType.parseMediaType("audio/mpeg");}
    return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
        .header(HttpHeaders.ACCEPT_RANGES,"bytes").header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(audio.originalName(),StandardCharsets.UTF_8).build().toString())
        .body(new org.springframework.core.io.FileSystemResource(path));
  }
  private void validate(ItemInput input){
    String mode=mode(input.mode()),section=input.section().trim();Set<String> allowed=mode.equals("PRACTICE")?PRACTICE:INTENSIVE;
    if(!allowed.contains(section))throw bad("题型与训练方式不匹配");if(!ANSWERS.contains(input.answerType()))throw bad("答案类型不正确");
    String level=repository.paperLevel(input.paperId());if((section.equals("NEWS")&&!level.equals("CET4"))||(section.equals("LECTURE")&&!level.equals("CET6")))throw bad("该题型不适用于所选考试级别");
    if(!repository.sectionExists(input.paperId(),mode,section))throw bad("该套卷不存在对应的题型资源槽位");
    if(input.audioStartMs()!=null&&input.audioEndMs()!=null&&input.audioEndMs()<=input.audioStartMs())throw bad("音频终点必须晚于起点");
  }
  private void validateAudio(MultipartFile file){if(file.isEmpty()||file.getSize()>200L*1024*1024)throw bad("音频为空或超过 200MB");String ext=StringUtils.getFilenameExtension(cleanName(file));if(ext==null||!AUDIO_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT)))throw bad("仅支持 MP3、M4A、WAV、OGG、WebM 或 AAC");}
  private String cleanName(MultipartFile file){return StringUtils.cleanPath(file.getOriginalFilename()==null?"audio":file.getOriginalFilename());}
  private String mode(String value){String result=value==null?"":value.toUpperCase(Locale.ROOT);if(!MODES.contains(result))throw bad("练习模式不正确");return result;}
  private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"题库内容不存在");}
}
