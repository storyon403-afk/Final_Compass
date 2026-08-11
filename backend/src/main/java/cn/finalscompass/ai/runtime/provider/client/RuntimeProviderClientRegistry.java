package cn.finalscompass.ai.runtime.provider.client;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public final class RuntimeProviderClientRegistry {
    private final Map<String, RuntimeProviderProtocolClient> clients;

    public RuntimeProviderClientRegistry(List<RuntimeProviderProtocolClient> values) {
        this.clients = values.stream().collect(Collectors.toUnmodifiableMap(
                RuntimeProviderProtocolClient::adapterKey, Function.identity(), (left, right) -> {
                    throw new IllegalStateException("Duplicate Runtime Provider adapter: " + left.adapterKey());
                }));
    }

    public RuntimeProviderProtocolClient require(String adapterKey) {
        RuntimeProviderProtocolClient client = clients.get(adapterKey);
        if (client == null) throw new IllegalStateException("Runtime Provider adapter is not registered: " + adapterKey);
        return client;
    }

    public Optional<RuntimeProviderProtocolClient> find(String adapterKey) {
        return Optional.ofNullable(clients.get(adapterKey));
    }
}
