package cn.finalscompass.ai;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiSkillRegistry {
    private final List<AiSkill> skills;

    public AiSkillRegistry(List<AiSkill> skills) { this.skills = List.copyOf(skills); }

    public List<SkillInfo> available() {
        return skills.stream().map(skill -> new SkillInfo(skill.id(), skill.displayName(), skill.description(), skill.maxInputLength())).toList();
    }

    public AiSkill require(String id) {
        return skills.stream().filter(skill -> skill.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知或未启用的 AI Skill"));
    }

    public record SkillInfo(String id, String name, String description, int maxInputLength) {}
}
