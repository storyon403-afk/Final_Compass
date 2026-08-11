package cn.finalscompass.ai.runtime.provider;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Runtime provider directory backed by the ai_runtime_provider registry. */
@Component
public final class RuntimeProviderCatalog {
    /** Hermes Agent Gateway is configured via the settings panel but dispatched outside the model mesh. */
    private static final String AGENT_RUNTIME_KEY = "hermes";

    private final JdbcClient jdbc;

    public RuntimeProviderCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProviderInfo> available() {
        List<ProviderInfo> values = new ArrayList<>(jdbc.sql("""
                SELECT provider_key,name,provider_type FROM ai_runtime_provider
                WHERE status='ACTIVE' ORDER BY provider_key
                """).query((rs, row) -> new ProviderInfo(rs.getString("provider_key"), rs.getString("name"),
                        Set.of(rs.getString("provider_type")))).list());
        values.add(new ProviderInfo(AGENT_RUNTIME_KEY, "Hermes Agent", Set.of("AGENT")));
        return values.stream().sorted(Comparator.comparing(ProviderInfo::id)).toList();
    }

    public List<ProviderInfo> availableModelProviders() {
        return available().stream().filter(item -> !item.capabilities().contains("AGENT")).toList();
    }

    public String require(String provider) {
        String normalized = normalize(provider);
        if (AGENT_RUNTIME_KEY.equals(normalized)) return normalized;
        boolean registered = jdbc.sql("SELECT TRUE FROM ai_runtime_provider WHERE provider_key=:key AND status='ACTIVE'")
                .param("key", normalized).query(Boolean.class).optional().orElse(false);
        if (!registered) throw new IllegalArgumentException("当前版本尚未注册该 AI Provider");
        return normalized;
    }

    public String normalize(String value) {
        String provider = value == null ? "" : value.trim().toLowerCase();
        if (!provider.matches("[a-z0-9][a-z0-9_-]{1,39}")) throw new IllegalArgumentException("Provider 标识不合法");
        return provider;
    }

    public record ProviderInfo(String id, String name, Set<String> capabilities) {}
}
