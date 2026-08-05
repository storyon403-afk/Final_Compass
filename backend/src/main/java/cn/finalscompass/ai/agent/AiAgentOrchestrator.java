package cn.finalscompass.ai.agent;

import org.springframework.stereotype.Component;

import cn.finalscompass.ai.agent.intent.IntentDecision;
import cn.finalscompass.ai.agent.intent.IntentValidator;
import cn.finalscompass.ai.guard.AiInputGuardrail;


/**
 * V2 orchestration facade:
 *
 * guard
 *   ↓
 * route
 *   ↓
 * validate intent
 *   ↓
 * plan
 */
@Component
public class AiAgentOrchestrator {


    private final AiInputGuardrail guardrail;

    private final AiIntentRouter router;

    private final IntentValidator intentValidator;

    private final AiSkillPlanner planner;



    public AiAgentOrchestrator(
            AiInputGuardrail guardrail,
            AiIntentRouter router,
            IntentValidator intentValidator,
            AiSkillPlanner planner
    ) {

        this.guardrail = guardrail;
        this.router = router;
        this.intentValidator = intentValidator;
        this.planner = planner;
    }



    public AiSkillPlanner.ExecutionPlan prepare(
            String requestedSkillId,
            String input
    ) {


        // 1. 输入安全检查
        AiInputGuardrail.GuardedInput guarded =
                guardrail.inspect(input);



        // 2. 意图识别
        IntentDecision decision =
                router.route(
                        requestedSkillId,
                        guarded.text()
                );



        // 3. 意图合法性校验
        decision =
                intentValidator.validate(decision);



        // 4. 根据意图生成执行计划
        return planner.plan(
                decision,
                guarded
        );
    }

}
