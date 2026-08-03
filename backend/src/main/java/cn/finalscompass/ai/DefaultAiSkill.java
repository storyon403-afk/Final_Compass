package cn.finalscompass.ai;

import java.util.Set;

/** Immutable V1 Skill definition registered by {@link AiSkillConfiguration}. */
public record DefaultAiSkill(String id, String category, String displayName, String description,
                             int maxInputLength, Set<String> modalities) implements AiSkill {
    public DefaultAiSkill {
        modalities = Set.copyOf(modalities);
    }
}
