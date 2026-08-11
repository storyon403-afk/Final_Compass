package cn.finalscompass.ai.runtime.provider.embedding;

import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.runtime.provider.RuntimeProviderCandidate;
import cn.finalscompass.ai.runtime.provider.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.time.Duration;
import java.util.*;

@Component
public final class OpenAiRuntimeEmbeddingClient implements RuntimeEmbeddingClient {
    private final RuntimeHttpTransport http;private final ObjectMapper json;
    public OpenAiRuntimeEmbeddingClient(RuntimeHttpTransport http,ObjectMapper json){this.http=http;this.json=json;}
    public String adapterKey(){return "openai-embeddings-v1";}
    public List<float[]> embed(RuntimeProviderCandidate candidate,ResolvedAiCredential credential,List<String> inputs){
        if(inputs==null||inputs.isEmpty()||inputs.size()>64||inputs.stream().anyMatch(v->v==null||v.isBlank()||v.length()>32000))throw new IllegalArgumentException("Embedding batch is invalid");
        if(!candidate.provider().key().equals(credential.provider())||!candidate.model().key().equals(credential.model()))throw new SecurityException("Embedding credential mismatch");
        try{String base=candidate.endpoint().baseUrl().replaceAll("/+$","");var response=http.postJson(new RuntimeHttpRequest(URI.create(base+"/v1/embeddings"),
                Duration.ofMillis(candidate.endpoint().connectTimeoutMs()),Duration.ofMillis(candidate.endpoint().requestTimeoutMs()),
                Map.of("Authorization","Bearer "+new String(credential.apiKey()),"Content-Type","application/json"),
                json.writeValueAsString(Map.of("model",candidate.model().key(),"input",inputs,"encoding_format","float")),16*1024*1024));
            if(response.statusCode()/100!=2)throw new IllegalStateException("Embedding Provider rejected request");var root=json.readTree(response.body());
            List<float[]> result=new ArrayList<>();for(var item:root.path("data")){var vector=item.path("embedding");float[] values=new float[vector.size()];for(int i=0;i<values.length;i++){double value=vector.get(i).asDouble(Double.NaN);if(!Double.isFinite(value))throw new IllegalStateException("Embedding contains invalid value");values[i]=(float)value;}result.add(values);}
            if(result.size()!=inputs.size())throw new IllegalStateException("Embedding result count mismatch");return List.copyOf(result);
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Embedding request failed",e);}
    }
}
