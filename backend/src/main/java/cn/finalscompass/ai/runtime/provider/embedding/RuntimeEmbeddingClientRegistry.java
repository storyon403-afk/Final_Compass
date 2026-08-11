package cn.finalscompass.ai.runtime.provider.embedding;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public final class RuntimeEmbeddingClientRegistry {
    private final Map<String,RuntimeEmbeddingClient> clients;
    public RuntimeEmbeddingClientRegistry(List<RuntimeEmbeddingClient> values){clients=values.stream().collect(Collectors.toUnmodifiableMap(RuntimeEmbeddingClient::adapterKey,Function.identity()));}
    public RuntimeEmbeddingClient require(String key){RuntimeEmbeddingClient value=clients.get(key);if(value==null)throw new IllegalStateException("Embedding adapter is unavailable: "+key);return value;}
}
