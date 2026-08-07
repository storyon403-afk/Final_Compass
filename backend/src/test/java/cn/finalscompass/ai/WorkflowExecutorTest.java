package cn.finalscompass.ai;

import cn.finalscompass.ai.agent.AiSkillPlanner;
import cn.finalscompass.ai.context.CourseContext;
import cn.finalscompass.ai.guard.AiInputGuardrail;
import cn.finalscompass.ai.guard.AiToolLimiter;
import cn.finalscompass.ai.skill.AiSkillRegistry;
import cn.finalscompass.ai.skill.DefaultAiSkill;
import cn.finalscompass.ai.task.LearningTask;
import cn.finalscompass.ai.task.LearningTaskType;
import cn.finalscompass.ai.workflow.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowExecutorTest {
    @Test void resolvesSkillsInOrderAndAddsVerifiedCourseContext() {
        var first = skill("material-summary"); var second = skill("learning-result-synthesis");
        var skills = new AiSkillRegistry(List.of(first, second));
        Workflow workflow = new DefaultWorkflow("material", LearningTaskType.MATERIAL_ANALYSIS, List.of(
                new WorkflowStep(1, first.id(), Set.of("MATERIALS")),
                new WorkflowStep(2, second.id(), Set.of("COURSE"))));
        // Use a focused planner call for execution behavior; registry completeness is tested separately.
        var planner = new AiSkillPlanner(skills, new AiToolLimiter());
        var task = new LearningTask("t1", LearningTaskType.MATERIAL_ANALYSIS, "分析资料", Set.of("COURSE"));
        var context = new CourseContext(1L, null, "数值分析",
                List.of(new CourseContext.Material(1, "期末讲义", "PPT", "迭代法")), null);
        var plan = planner.planWorkflow(task, workflow, context, new AiInputGuardrail().inspect("分析资料"));
        assertEquals(List.of(first.id(), second.id()), plan.skillSequence());
        assertEquals(second.id(), plan.primarySkill().id());
        assertTrue(plan.userInput().contains("数值分析"));
        assertTrue(plan.userInput().contains("期末讲义"));
    }

    @Test void registryRejectsMissingTaskWorkflows() {
        var skill = skill("progressive-hint");
        var skills = new AiSkillRegistry(List.of(skill));
        Workflow only = new DefaultWorkflow("question", LearningTaskType.QUESTION_ASSISTANCE,
                List.of(new WorkflowStep(1, skill.id(), Set.of())));
        assertThrows(IllegalStateException.class, () -> new WorkflowRegistry(List.of(only), skills));
    }

    private DefaultAiSkill skill(String id) {
        return new DefaultAiSkill(id, "TEST", id, id, 1000, Set.of("TEXT"),
                "instruction " + id, "contract " + id, Set.of());
    }
}
