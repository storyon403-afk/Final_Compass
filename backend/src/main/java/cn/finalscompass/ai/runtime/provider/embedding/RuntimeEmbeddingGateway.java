package cn.finalscompass.ai.runtime.provider.embedding;

import cn.finalscompass.ai.runtime.provider.*;
import cn.finalscompass.service.AiCredentialResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public final class RuntimeEmbeddingGateway {
    private final RuntimeProviderMatcher providers;private final RuntimeEmbeddingClientRegistry clients;private final AiCredentialResolver credentials;private final ObjectMapper json;
    public RuntimeEmbeddingGateway(RuntimeProviderMatcher providers,RuntimeEmbeddingClientRegistry clients,AiCredentialResolver credentials,ObjectMapper json){this.providers=providers;this.clients=clients;this.credentials=credentials;this.json=json;}
    public EmbeddingBatch embed(List<String> inputs){List<RuntimeProviderCandidate> candidates=providers.match(new ProviderSelectionRequest(Set.of("EMBEDDING"),0,0,false,false,Set.of(RuntimeProviderType.API),Set.of(),"PLATFORM"));
        RuntimeException last=null;for(var candidate:candidates)try{var config=json.readTree(candidate.model().configurationJson());String adapter=config.path("embeddingAdapterKey").asText();int expected=config.path("embeddingDimension").asInt(0);if(adapter.isBlank()||expected<1)continue;
            try(var credential=credentials.resolvePlatformService(candidate.provider().key(),candidate.model().key())){List<float[]> vectors=clients.require(adapter).embed(candidate,credential,inputs);if(vectors.stream().anyMatch(v->v.length!=expected))throw new IllegalStateException("Embedding dimension mismatch");return new EmbeddingBatch(candidate.provider().key(),candidate.model().key(),expected,vectors);}
        }catch(RuntimeException e){last=e;}catch(Exception e){last=new IllegalStateException(e);}throw new IllegalStateException("No Embedding Provider completed the request",last);}
    public record EmbeddingBatch(String providerKey,String modelKey,int dimension,List<float[]> vectors){public EmbeddingBatch{vectors=List.copyOf(vectors);}}
}
