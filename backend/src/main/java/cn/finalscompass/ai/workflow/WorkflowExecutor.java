package cn.finalscompass.ai.workflow;

import cn.finalscompass.ai.agent.AiSkillPlanner;
import cn.finalscompass.ai.context.CourseContext;
import cn.finalscompass.ai.guard.AiInputGuardrail;
import cn.finalscompass.ai.task.LearningTask;
import org.springframework.stereotype.Component;

/** Resolves and composes atomic Skills into one provider-neutral executable plan. */
@Component
public class WorkflowExecutor {
    private final WorkflowRegistry workflows;
    private final AiSkillPlanner planner;
    public WorkflowExecutor(WorkflowRegistry workflows, AiSkillPlanner planner) {
        this.workflows = workflows; this.planner = planner;
    }
    public AiSkillPlanner.ExecutionPlan prepare(LearningTask task, CourseContext context,
                                                AiInputGuardrail.GuardedInput input) {
        return planner.planWorkflow(task, workflows.require(task.taskType()), context, input);
    }
}
