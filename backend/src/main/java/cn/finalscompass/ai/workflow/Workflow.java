package cn.finalscompass.ai.workflow;

import cn.finalscompass.ai.task.LearningTaskType;
import java.util.List;

public interface Workflow {
    String id();
    LearningTaskType taskType();
    List<WorkflowStep> steps();
}
