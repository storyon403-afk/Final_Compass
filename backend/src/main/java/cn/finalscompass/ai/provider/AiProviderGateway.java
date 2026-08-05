package cn.finalscompass.ai;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Selects a registered provider adapter without exposing provider-specific logic to Skills. */
@Component
public class AiProviderGateway {
    private final Map<String, AiProviderAdapter> adapters;

    public AiProviderGateway(List<AiProviderAdapter> adapters) {
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(
                AiProviderAdapter::id, Function.identity(), (left, right) -> {
                    throw new IllegalStateException("AI Provider ID 重复: " + left.id());
                }));
    }

    public List<ProviderInfo> available() {
        return adapters.values().stream().sorted(Comparator.comparing(AiProviderAdapter::id))
                .map(adapter -> new ProviderInfo(adapter.id(), adapter.displayName(), adapter.capabilities()))
                .toList();
    }

    public AiProviderAdapter require(String provider) {
        String normalized = normalize(provider);
        AiProviderAdapter adapter = adapters.get(normalized);
        if (adapter == null) throw new IllegalArgumentException("当前版本尚未注册该 AI Provider");
        return adapter;
    }

    public AiProviderAdapter.AiProviderResult invoke(String provider, String model,
                                                      AiSkillPlanner.ExecutionPlan plan, char[] apiKey,
                                                      AiProviderAdapter.TransientImage image) {
        AiProviderAdapter adapter = require(provider);
        AiSkill skill = plan.primarySkill();
        if ("VISION".equals(skill.category()) && !adapter.capabilities().contains("IMAGE")) {
            throw new IllegalArgumentException(adapter.displayName() + " 当前配置不支持图片 Skill");
        }
        return adapter.invoke(new AiProviderAdapter.AiProviderRequest(model, plan, image), apiKey);
    }

    public AiProviderAdapter.AiProviderResult invoke(String provider, String model,
                                                      AiSkillPlanner.ExecutionPlan plan, char[] apiKey) {
        return invoke(provider, model, plan, apiKey, null);
    }

    public String normalize(String value) {
        String provider = value == null ? "" : value.trim().toLowerCase();
        if (!provider.matches("[a-z0-9][a-z0-9_-]{1,39}")) throw new IllegalArgumentException("Provider 标识不合法");
        return provider;
    }

    public record ProviderInfo(String id, String name, java.util.Set<String> capabilities) {}
}
