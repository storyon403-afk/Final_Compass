package cn.finalscompass.ai.workflow;

import cn.finalscompass.ai.task.LearningTaskType;
import java.util.List;

public record DefaultWorkflow(String id, LearningTaskType taskType, List<WorkflowStep> steps) implements Workflow {
    public DefaultWorkflow { steps = List.copyOf(steps); }
}
