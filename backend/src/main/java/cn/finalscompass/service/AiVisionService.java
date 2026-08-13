package cn.finalscompass.service;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderDefinitionRepository;
import cn.finalscompass.ai.runtime.provider.client.RuntimeBinaryInput;
import cn.finalscompass.ai.runtime.provider.client.RuntimeProviderClientRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 执行独立视觉预处理，将图片转换成可安全交给主回答模型的结构化文本。 */
@Service
public class AiVisionService {
  private final org.springframework.jdbc.core.simple.JdbcClient jdbc;private final AiCredentialResolver credentials;private final RuntimeProviderDefinitionRepository providers;private final RuntimeProviderClientRegistry clients;
  public AiVisionService(org.springframework.jdbc.core.simple.JdbcClient jdbc,AiCredentialResolver credentials,RuntimeProviderDefinitionRepository providers,RuntimeProviderClientRegistry clients){this.jdbc=jdbc;this.credentials=credentials;this.providers=providers;this.clients=clients;}
  public VisionResult analyze(long userId,MultipartFile file,String providerValue,String modelValue,String sourceValue,String ephemeral){
    if(file==null||file.isEmpty()||file.getSize()>10*1024*1024||file.getContentType()==null||!file.getContentType().startsWith("image/"))throw new IllegalArgumentException("请选择 10MB 以内的图片");
    AiCredentialSource source;try{source=AiCredentialSource.valueOf(sourceValue);}catch(Exception e){throw new IllegalArgumentException("视觉凭据来源不合法");}
    // 功能开关只控制用户自带视觉链路；平台额度始终服从管理员配置的平台视觉通道。
    boolean enabled=jdbc.sql("SELECT user_vision_auxiliary_enabled FROM ai_feature_setting WHERE id=1").query(Boolean.class).single();if(source!=AiCredentialSource.PLATFORM&&!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"管理员已关闭用户视觉辅助功能");
    if(source==AiCredentialSource.EPHEMERAL_BYOK&&!jdbc.sql("SELECT user_vision_ephemeral_key_enabled FROM ai_feature_setting WHERE id=1").query(Boolean.class).single())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"管理员已关闭临时视觉 Key");
    var provider=providers.findRoutableByKey(providerValue).orElseThrow(()->new IllegalArgumentException("视觉 Provider 不可用"));
    var model=provider.models().stream().filter(item->item.key().equals(modelValue)&&item.capabilities().contains("VISION")).findFirst().orElseThrow(()->new IllegalArgumentException("所选模型未注册视觉能力"));
    var endpoint=provider.endpoints().getFirst();
    var command=new RuntimeModelInvocationCommand(provider.id(),provider.key(),provider.type(),provider.adapterKey(),model.id(),model.key(),endpoint.id(),endpoint.key(),endpoint.baseUrl(),source.name(),"user-image-analysis","1.0.0","你是严谨的图片识别模型。完整提取图片中的文字、公式、表格、条件和问题，标记不确定区域；使用 Markdown 输出，不要解答题目。","请识别这张图片。","{}","MARKDOWN","{}",Set.of(),List.of(),Set.of("TEXT","IMAGE"),false,BigDecimal.ZERO,BigDecimal.ZERO,"CNY",endpoint.connectTimeoutMs(),endpoint.requestTimeoutMs());
    try(var credential=credentials.resolveUserVision(userId,provider.key(),model.key(),source,ephemeral);var binary=new RuntimeBinaryInput(file.getContentType(),file.getBytes())){var result=clients.require(provider.adapterKey()).invoke(command,credential,binary);return new VisionResult(file.getOriginalFilename(),provider.key(),model.key(),result.content());}catch(java.io.IOException e){throw new IllegalArgumentException("无法读取图片",e);}
  }
  public record VisionResult(String fileName,String provider,String model,String markdown){}
}
