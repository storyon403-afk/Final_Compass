package cn.finalscompass.ai.agent;

import org.springframework.stereotype.Component;

import cn.finalscompass.ai.agent.intent.IntentDecision;
import cn.finalscompass.ai.agent.intent.IntentValidator;
import cn.finalscompass.ai.guard.AiInputGuardrail;
import cn.finalscompass.ai.context.CourseContext;
import cn.finalscompass.ai.task.LearningTaskRouter;
import cn.finalscompass.ai.workflow.WorkflowExecutor;
import cn.finalscompass.ai.task.LearningTaskType;


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
    private final LearningTaskRouter learningTasks;
    private final WorkflowExecutor workflows;



    public AiAgentOrchestrator(
            AiInputGuardrail guardrail,
            AiIntentRouter router,
            IntentValidator intentValidator,
            AiSkillPlanner planner,
            LearningTaskRouter learningTasks,
            WorkflowExecutor workflows
    ) {

        this.guardrail = guardrail;
        this.router = router;
        this.intentValidator = intentValidator;
        this.planner = planner;
        this.learningTasks = learningTasks;
        this.workflows = workflows;
    }



    public AiSkillPlanner.ExecutionPlan prepare(
            String requestedSkillId,
            String input
    ) {


        return prepare(requestedSkillId, input, CourseContext.empty());
    }

    public AiSkillPlanner.ExecutionPlan prepare(String requestedSkillId, String input, CourseContext context) {
        // 1. 输入安全检查
        AiInputGuardrail.GuardedInput guarded =
                guardrail.inspect(input);

        if (requestedSkillId == null || requestedSkillId.isBlank() || "auto".equalsIgnoreCase(requestedSkillId)) {
            var learningTask = learningTasks.route(guarded.text());
            // Preserve the mature fine-grained math/statistics intent routing for ordinary questions.
            if (learningTask.taskType() != LearningTaskType.QUESTION_ASSISTANCE)
                return workflows.prepare(learningTask, context, guarded);
        }



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
