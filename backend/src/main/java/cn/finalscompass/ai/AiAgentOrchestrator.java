package cn.finalscompass.ai;

import org.springframework.stereotype.Component;

/** V2 orchestration facade: guard -> route -> plan. */
@Component
public class AiAgentOrchestrator {
    private final AiInputGuardrail guardrail;
    private final AiIntentRouter router;
    private final AiSkillPlanner planner;

    public AiAgentOrchestrator(AiInputGuardrail guardrail, AiIntentRouter router, AiSkillPlanner planner) {
        this.guardrail = guardrail;
        this.router = router;
        this.planner = planner;
    }

    public AiSkillPlanner.ExecutionPlan prepare(String requestedSkillId, String input) {
        AiInputGuardrail.GuardedInput guarded = guardrail.inspect(input);
        return planner.plan(router.route(requestedSkillId, guarded.text()), guarded);
    }
}
