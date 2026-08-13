package cn.finalscompass.controller;

import cn.finalscompass.service.AiAnalysisService;
import cn.finalscompass.service.AiDocumentConversionService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiAnalysisController {
  private final AuthService auth;
  private final AiAnalysisService ai;
  private final AiDocumentConversionService documents;
  private final cn.finalscompass.service.AiVisionService vision;

  public AiAnalysisController(
      AuthService auth, AiAnalysisService ai, AiDocumentConversionService documents,cn.finalscompass.service.AiVisionService vision) {
    this.auth = auth;
    this.ai = ai;
    this.documents = documents;
    this.vision=vision;
  }

  @GetMapping("/dashboard")
  public AiAnalysisService.Dashboard dashboard(HttpServletRequest request) {
    return ai.dashboard(auth.current(request).id());
  }

  @PutMapping("/byok")
  public Map<String, Object> saveByok(
      HttpServletRequest request, @RequestBody AiAnalysisService.SaveUserKey body) {
    return ai.saveUserKey(auth.current(request).id(), body);
  }

  @DeleteMapping("/byok/{provider}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteByok(HttpServletRequest request, @PathVariable String provider) {
    ai.deleteUserKey(auth.current(request).id(), provider);
  }

  @PutMapping("/review-byok")
  public Map<String, Object> saveReviewByok(
      HttpServletRequest request, @RequestBody AiAnalysisService.SaveUserKey body) {
    return ai.saveUserReviewKey(auth.current(request).id(), body);
  }

  @PutMapping("/vision-byok") public Map<String,Object> saveVisionByok(HttpServletRequest request,@RequestBody AiAnalysisService.SaveUserKey body){return ai.saveUserVisionKey(auth.current(request).id(),body);}
  @DeleteMapping("/vision-byok/{provider}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteVisionByok(HttpServletRequest request,@PathVariable String provider){ai.deleteUserVisionKey(auth.current(request).id(),provider);}
  @PutMapping("/admin/vision-features") public Map<String,Object> updateVisionFeatures(HttpServletRequest request,@RequestBody AiAnalysisService.VisionFeatureUpdate body){return ai.updateVisionFeatures(auth.requireAdmin(request).id(),body);}

  @PutMapping("/admin/platform-key")
  public Map<String, Object> savePlatformKey(
      HttpServletRequest request, @RequestBody AiAnalysisService.SavePlatformKey body) {
    return ai.savePlatformKey(auth.requireAdmin(request).id(), body);
  }

  @PutMapping("/admin/platform-default")
  public Map<String, Object> savePlatformDefault(
      HttpServletRequest request, @RequestBody AiAnalysisService.PlatformDefaultRequest body) {
    return ai.savePlatformDefault(auth.requireAdmin(request).id(), body);
  }

  @PutMapping("/admin/platform-review-key")
  public Map<String, Object> savePlatformReviewKey(
      HttpServletRequest request, @RequestBody AiAnalysisService.SavePlatformKey body) {
    return ai.savePlatformReviewKey(auth.requireAdmin(request).id(), body);
  }

  @PostMapping(
      value = "/attachments/convert",
      consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  public AiDocumentConversionService.ConversionResult convertAttachment(
      HttpServletRequest request,
      @RequestPart org.springframework.web.multipart.MultipartFile file) {
    auth.current(request);
    return documents.convert(file);
  }
  @PostMapping(value="/vision/analyze",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  public cn.finalscompass.service.AiVisionService.VisionResult analyzeVision(HttpServletRequest request,@RequestPart org.springframework.web.multipart.MultipartFile file,@RequestParam String provider,@RequestParam String model,@RequestParam String credentialSource,@RequestParam(required=false) String ephemeralApiKey){return vision.analyze(auth.current(request).id(),file,provider,model,credentialSource,ephemeralApiKey);}
}
