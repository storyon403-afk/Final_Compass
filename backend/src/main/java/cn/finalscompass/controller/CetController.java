package cn.finalscompass.controller;

import cn.finalscompass.cet.application.CetQueryHandler;
import cn.finalscompass.cet.application.CetPaperHandler;
import cn.finalscompass.cet.application.CetItemHandler;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供 CET 试卷、题目和音频资料访问，以及受管理员权限保护的内容维护接口 * 上传文件始终限制在配置的上传目录内，避免客户端输入造成路径越界 */
@RestController
@RequestMapping("/api/cet")
public class CetController {
  private final CetQueryHandler queries;
  private final CetPaperHandler papers;
  private final CetItemHandler itemCommands;

  public CetController(
      CetQueryHandler queries, CetPaperHandler papers, CetItemHandler itemCommands) {
    this.queries = queries;
    this.papers = papers;
    this.itemCommands = itemCommands;
  }

  @GetMapping("/papers")
  public List<CetPaper> papers(@RequestParam(required = false) String level) {
    return queries.papers(level);
  }

  @GetMapping("/items")
  public List<CetItem> items(
      @RequestParam String level,
      @RequestParam String mode,
      @RequestParam(required = false) String section) {
    return queries.items(level, mode, section);
  }

  @GetMapping("/admin/items")
  public List<CetItem> adminItems(@Authenticated AuthService.CurrentUser user) {
    return queries.adminItems(user);
  }

  @GetMapping("/admin/sections")
  public List<SectionResource> adminSections(@Authenticated AuthService.CurrentUser user) {
    return queries.sections(user);
  }

  @DeleteMapping("/admin/sections/{id}/content")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clearSection(@Authenticated AuthService.CurrentUser user, @PathVariable long id) {
    papers.clearSection(user, id);
  }

  @PostMapping("/papers")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Long> createPaper(
      @Authenticated AuthService.CurrentUser user, @Valid @RequestBody PaperInput input) {
    return papers.create(user, input);
  }

  @PutMapping("/papers/{id}")
  public void updatePaper(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable long id,
      @Valid @RequestBody PaperInput input) {
    papers.update(user, id, input);
  }

  @DeleteMapping("/papers/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePaper(@Authenticated AuthService.CurrentUser user, @PathVariable long id) {
    papers.delete(user, id);
  }

  /** 为分类练习和精听精讲绑定整套共用音频；不属于完整套卷下载资料 */
  @PostMapping("/papers/{id}/practice-audio")
  public void uploadPracticeAudio(
      @Authenticated AuthService.CurrentUser user, @PathVariable long id, @RequestPart MultipartFile file)
      throws IOException {
    itemCommands.uploadPracticeAudio(user, id, file);
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Long> createItem(
      @Authenticated AuthService.CurrentUser user, @Valid @RequestBody ItemInput input) {
    return itemCommands.create(user, input);
  }

  @PutMapping("/items/{id}")
  public void updateItem(
      @Authenticated AuthService.CurrentUser user,
      @PathVariable long id,
      @Valid @RequestBody ItemInput input) {
    itemCommands.update(user, id, input);
  }

  @PostMapping("/items/{id}/audio")
  public void uploadAudio(
      @Authenticated AuthService.CurrentUser user, @PathVariable long id, @RequestPart MultipartFile file)
      throws IOException {
    itemCommands.uploadItemAudio(user, id, file);
  }

  @GetMapping("/items/{id}/audio")
  public ResponseEntity<org.springframework.core.io.Resource> audio(@PathVariable long id)
      throws IOException {
    return itemCommands.audio(id);
  }

  public record CetPaper(
      long id,
      String level,
      int examYear,
      int examMonth,
      int setNumber,
      String title,
      boolean published,
      String sourceName,
      String sourcePageUrl,
      String usageNote,
      String audioOriginalName,
      boolean audioAvailable) {}

  public record CetItem(
      long id,
      long paperId,
      String level,
      int examYear,
      int examMonth,
      int setNumber,
      String paperTitle,
      String mode,
      String section,
      String title,
      String prompt,
      String passage,
      String translation,
      String analysis,
      String keySentence,
      String answerType,
      String optionsJson,
      String correctAnswer,
      int itemOrder,
      String audioOriginalName,
      Integer audioStartMs,
      Integer audioEndMs) {}

  public record PaperInput(
      @NotBlank String level,
      int examYear,
      int examMonth,
      int setNumber,
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 120) String sourceName,
      @NotBlank @Size(max = 500) String sourcePageUrl,
      @Size(max = 500) String usageNote) {}

  public record ItemInput(
      @NotNull Long paperId,
      @NotBlank String mode,
      @NotBlank @Size(max = 32) String section,
      @NotBlank @Size(max = 160) String title,
      String prompt,
      String passage,
      String translation,
      String analysis,
      String keySentence,
      @NotBlank String answerType,
      String optionsJson,
      String correctAnswer,
      int itemOrder,
      Integer audioStartMs,
      Integer audioEndMs) {}

  public record SectionResource(long id, long paperId, String paperTitle, String level,
      String mode, String section, long itemCount) {}

}
