package cn.finalscompass.ai.guard;

import cn.finalscompass.ai.skill.AiSkill;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Fail-closed allowlist for tools a Skill may request. V2 plans tools but does not execute MCP yet. */
@Component
public class AiToolLimiter {
    private static final Set<String> REGISTERED_TOOLS = Set.of(
            "CourseTools.find", "MaterialTools.search", "MaterialTools.read");

    public Set<String> allowedFor(AiSkill skill) {
        if (!REGISTERED_TOOLS.containsAll(skill.allowedTools())) {
            throw new IllegalStateException("Skill 引用了未注册的工具: " + skill.id());
        }
        return skill.allowedTools();
    }
}
