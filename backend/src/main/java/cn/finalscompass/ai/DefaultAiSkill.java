package cn.finalscompass.ai;

import java.util.Set;

/** Immutable executable V2 Skill definition registered by {@link AiSkillConfiguration}. */
public record DefaultAiSkill(String id, String category, String displayName, String description,
                             int maxInputLength, Set<String> modalities, String systemInstruction,
                             String outputContract, Set<String> allowedTools) implements AiSkill {
    public DefaultAiSkill {
        modalities = Set.copyOf(modalities);
        allowedTools = Set.copyOf(allowedTools);
    }
}
