package cn.finalscompass.controller;

import cn.finalscompass.service.AiAnalysisService;
import cn.finalscompass.service.AiDocumentConversionService;
import cn.finalscompass.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


/**
 * 在这里介绍一下restController的具体基础设计，后面的类便不再介绍
 */

//创建spring mvc对象
@RestController
//访问路径匹配（请求映射方法）
@RequestMapping("/api/ai")
public class AiAnalysisController {

  //声明
  /**
   * 1.Makes immutability explicit and compiler-enforced(no accidental field mutation in some later method)
   * 2.Guarantees safe pubilcation
   * 3.Forces constructor in jection rather than field/setter injection 
   */
  private final AuthService auth;
  private final AiAnalysisService ai;
  private final AiDocumentConversionService documents;
  private final cn.finalscompass.service.AiVisionService vision;
  
  // 注入
  public AiAnalysisController(
      AuthService auth, AiAnalysisService ai, AiDocumentConversionService documents,cn.finalscompass.service.AiVisionService vision) {
    this.auth = auth;
    this.ai = ai;
    this.documents = documents;
    this.vision=vision;
  }

  //查询（GET有参请求API注解）， @GetMapping默认继承@RequestMapping("/api/ai")的路径
  // RESTful 路径传值方式("/{dashboard}")，http://localhost:8080/api/ai/1  dashboard=1
  //对比普通传参：http://localhost:8080/api/ai?dashboard=1&byok=2 dashboard=1 byok=2
  //相同注解，参数必须不一
  @GetMapping("/dashboard")
  public AiAnalysisService.Dashboard dashboard(HttpServletRequest request) {
    return ai.dashboard(auth.current(request).id());
  }

  // 更新请求 http://localhost:8080/ai/api/byok(局部pacth)
  @PutMapping("/byok")
  public Map<String, Object> saveByok(
      HttpServletRequest request, @RequestBody AiAnalysisService.SaveUserKey body) {
    return ai.saveUserKey(auth.current(request).id(), body);
  }

  // 删除请求 http://localhost:8080/ai/api/byok/{provider}
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

  @PutMapping("/admin/internal-test-access")
  public Map<String, Object> updateInternalTestAccess(
      HttpServletRequest request, @RequestBody AiAnalysisService.InternalTestAccessRequest body) {
    return ai.updateInternalTestAccess(auth.requireAdmin(request).id(), body);
  }

  @PutMapping("/admin/usage-policy")
  public Map<String,Object> updateUsagePolicy(HttpServletRequest request,
      @RequestBody AiAnalysisService.UsagePolicyRequest body) {
    return ai.updateUsagePolicy(auth.requireAdmin(request).id(), body);
  }

  @PutMapping("/admin/platform-review-key")
  public Map<String, Object> savePlatformReviewKey(
      HttpServletRequest request, @RequestBody AiAnalysisService.SavePlatformKey body) {
    return ai.savePlatformReviewKey(auth.requireAdmin(request).id(), body);
  }

  // 创建请求 http://localhost:8080/ai/api/attachments/convert
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
