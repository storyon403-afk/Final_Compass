package cn.finalscompass.ai.workflow;

import java.util.Set;

public record WorkflowStep(int stepOrder, String skillId, Set<String> requiredContext) {
    public WorkflowStep { requiredContext = Set.copyOf(requiredContext); }
}
