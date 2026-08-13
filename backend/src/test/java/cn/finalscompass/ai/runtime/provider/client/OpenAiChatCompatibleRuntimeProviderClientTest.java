package cn.finalscompass.ai.runtime.provider.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.model.RuntimeModelInvocationCommand;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenAiChatCompatibleRuntimeProviderClientTest {
  @Test
  void usesTheSameProtocolForDeepSeekKimiAndQwen() {
    for (String provider : List.of("deepseek", "kimi", "qwen")) {
      RecordingTransport transport =
          new RecordingTransport(
              new RuntimeHttpResponse(
                  200,
                  Map.of("x-request-id", List.of(provider + "-request")),
                  "{\"choices\":[{\"message\":{\"content\":\"answer\"}}],"
                      + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}"));
      var client =
          new OpenAiChatCompatibleRuntimeProviderClient(new ObjectMapper(), transport);
      try (ResolvedAiCredential credential = credential(provider)) {
        RuntimeProviderClientResult result =
            client.invoke(command(provider, "https://proxy.example.com/v1/"), credential, null);
        assertEquals("answer", result.content());
        assertEquals(12, result.inputUnits());
        assertEquals(7, result.outputUnits());
      }
      assertEquals(
          "https://proxy.example.com/v1/chat/completions", transport.request.uri().toString());
      assertTrue(transport.request.headers().get("Authorization").startsWith("Bearer "));
      assertFalse(transport.request.body().contains("test-secret-key"));
    }
  }

  @Test
  void rejectsMismatchedCredentialAndUnsupportedCapabilitiesBeforeTransport() {
    RecordingTransport transport =
        new RecordingTransport(new RuntimeHttpResponse(200, Map.of(), "{}"));
    var client = new OpenAiChatCompatibleRuntimeProviderClient(new ObjectMapper(), transport);
    try (ResolvedAiCredential credential = credential("qwen")) {
      assertThrows(
          SecurityException.class,
          () -> client.invoke(command("kimi", "https://example.com/v1"), credential, null));
    }
    assertEquals(0, transport.calls);
  }

  @Test
  void sendsDoubaoImageAsDataUrlContentParts() {
    RecordingTransport transport=new RecordingTransport(new RuntimeHttpResponse(200,Map.of(),"{\"choices\":[{\"message\":{\"content\":\"识别结果\"}}]}"));
    var client=new OpenAiChatCompatibleRuntimeProviderClient(new ObjectMapper(),transport);
    try(ResolvedAiCredential credential=new ResolvedAiCredential("doubao","doubao-seed-2-1-turbo-260628",AiCredentialSource.PLATFORM,"test-secret-key".toCharArray());RuntimeBinaryInput image=new RuntimeBinaryInput("image/png",new byte[]{1,2,3})){
      client.invoke(imageCommand(),credential,image);
    }
    assertTrue(transport.request.body().contains("image_url"));
    assertTrue(transport.request.body().contains("data:image/png;base64,AQID"));
    assertTrue(transport.request.body().contains("doubao-seed-2-1-turbo-260628"));
    assertFalse(transport.request.body().contains("test-secret-key"));
  }

  private RuntimeModelInvocationCommand command(String provider, String baseUrl) {
    return new RuntimeModelInvocationCommand(
        1,
        provider,
        RuntimeProviderType.API,
        OpenAiChatCompatibleRuntimeProviderClient.ADAPTER_KEY,
        2,
        "test-model",
        3,
        "default",
        baseUrl,
        "PLATFORM",
        "course-help",
        "1.0.0",
        "system",
        "question",
        "{}",
        "output",
        "{}",
        Set.of(),
        List.of(),
        Set.of("TEXT"),
        false,
        new BigDecimal("0.01"),
        new BigDecimal("0.02"),
        "USD",
        1500,
        25000);
  }

  private RuntimeModelInvocationCommand imageCommand(){
    return new RuntimeModelInvocationCommand(1,"doubao",RuntimeProviderType.API,OpenAiChatCompatibleRuntimeProviderClient.ADAPTER_KEY,2,"doubao-seed-2-1-turbo-260628",3,"default","https://ark.cn-beijing.volces.com/api/v3","PLATFORM","image","1.0.0","system","识别图片","{}","MARKDOWN","{}",Set.of(),List.of(),Set.of("TEXT","IMAGE"),false,BigDecimal.ZERO,BigDecimal.ZERO,"CNY",1500,25000);
  }

  private ResolvedAiCredential credential(String provider) {
    return new ResolvedAiCredential(
        provider, "test-model", AiCredentialSource.PLATFORM, "test-secret-key".toCharArray());
  }

  private static final class RecordingTransport implements RuntimeHttpTransport {
    private final RuntimeHttpResponse response;
    private RuntimeHttpRequest request;
    private int calls;

    private RecordingTransport(RuntimeHttpResponse response) {
      this.response = response;
    }

    @Override
    public RuntimeHttpResponse postJson(RuntimeHttpRequest request) {
      this.request = request;
      calls++;
      return response;
    }
  }
}
