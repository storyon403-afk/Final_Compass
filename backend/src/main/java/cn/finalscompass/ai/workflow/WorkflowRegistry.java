package cn.finalscompass.ai.workflow;

import cn.finalscompass.ai.skill.AiSkillRegistry;
import cn.finalscompass.ai.task.LearningTaskType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkflowRegistry {
    private final Map<LearningTaskType, Workflow> workflows;
    public WorkflowRegistry(List<Workflow> values, AiSkillRegistry skills) {
        var registered = new EnumMap<LearningTaskType, Workflow>(LearningTaskType.class);
        for (Workflow workflow : values) {
            if (registered.put(workflow.taskType(), workflow) != null)
                throw new IllegalStateException("Learning Task Workflow 重复: " + workflow.taskType());
            int previous = 0;
            for (WorkflowStep step : workflow.steps()) {
                if (step.stepOrder() <= previous) throw new IllegalStateException("Workflow 步骤顺序不合法: " + workflow.id());
                skills.require(step.skillId());
                previous = step.stepOrder();
            }
        }
        for (LearningTaskType type : LearningTaskType.values())
            if (!registered.containsKey(type)) throw new IllegalStateException("缺少 Learning Task Workflow: " + type);
        workflows = Map.copyOf(registered);
    }
    public Workflow require(LearningTaskType type) { return workflows.get(type); }
}
