package cn.finalscompass.ai.runtime.provider.embedding;

import cn.finalscompass.ai.credential.*;
import cn.finalscompass.ai.runtime.provider.*;
import cn.finalscompass.ai.runtime.provider.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiRuntimeEmbeddingClientTest {
    @Test void sendsBatchAndParsesFiniteVectorsWithoutLeakingKeyIntoBody(){
        CapturingHttp http=new CapturingHttp();var client=new OpenAiRuntimeEmbeddingClient(http,new ObjectMapper());
        try(var credential=new ResolvedAiCredential("openai","text-embedding-3-small",AiCredentialSource.PLATFORM,"secret-key".toCharArray())){
            List<float[]> result=client.embed(candidate(),credential,List.of("第一段","第二段"));
            assertEquals(2,result.size());assertArrayEquals(new float[]{1,0},result.getFirst());
            assertFalse(http.request.body().contains("secret-key"));assertTrue(http.request.headers().get("Authorization").contains("secret-key"));
        }
    }
    private RuntimeProviderCandidate candidate(){var endpoint=new RuntimeProviderEndpoint(1,"default","https://api.openai.com",null,1,1,RuntimeProviderStatus.ACTIVE,1000,10000,null,"{}");var model=new RuntimeProviderModel(2,"text-embedding-3-small","embedding",RuntimeProviderModelStatus.ACTIVE,8191,null,false,false,null,null,null,1,1,"{}",Set.of("EMBEDDING"));var provider=new RuntimeProviderDefinition(3,"openai","OpenAI",RuntimeProviderType.API,"openai-responses-v1",RuntimeProviderStatus.ACTIVE,Set.of("PLATFORM"),"{}","{}",List.of(endpoint),List.of(model));return new RuntimeProviderCandidate(provider,model,endpoint);}
    private static final class CapturingHttp implements RuntimeHttpTransport {RuntimeHttpRequest request;public RuntimeHttpResponse postJson(RuntimeHttpRequest request){this.request=request;return new RuntimeHttpResponse(200,Map.of(),"{\"data\":[{\"embedding\":[1,0]},{\"embedding\":[0,1]}]}");}}
}
