package cn.finalscompass.ai.skill;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiSkillRegistry {
    private final Map<String, AiSkill> skills;

    public AiSkillRegistry(List<AiSkill> skills) {
        this.skills = skills.stream().collect(Collectors.toUnmodifiableMap(
                AiSkill::id, Function.identity(), (left, right) -> {
                    throw new IllegalStateException("AI Skill ID 重复: " + left.id());
                }));
    }

    public List<SkillInfo> available() {
        return skills.values().stream()
                .sorted(java.util.Comparator.comparing(AiSkill::category).thenComparing(AiSkill::id))
                .map(skill -> new SkillInfo(skill.id(), skill.category(), skill.displayName(), skill.description(),
                        skill.maxInputLength(), skill.modalities()))
                .toList();
    }

    public AiSkill require(String id) {
        AiSkill skill = skills.get(id);
        if (skill == null) throw new IllegalArgumentException("未知或未启用的 AI Skill");
        return skill;
    }

    public record SkillInfo(String id, String category, String name, String description,
                            int maxInputLength, java.util.Set<String> modalities) {}
}
