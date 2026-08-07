package cn.finalscompass.ai.task;

import java.util.Set;

/** Product-level learning goal inferred from the user's natural-language request. */
public record LearningTask(String taskId, LearningTaskType taskType, String description,
                           Set<String> requiredContext) {
    public LearningTask {
        requiredContext = Set.copyOf(requiredContext);
    }
}
