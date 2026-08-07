package cn.finalscompass.ai.workflow;

import cn.finalscompass.ai.task.LearningTaskType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class WorkflowConfiguration {
    @Bean Workflow examPreparationWorkflow() {
        return workflow("exam-preparation", LearningTaskType.EXAM_PREPARATION,
                step(1, "material-summary", "COURSE", "MATERIALS"),
                step(2, "exam-focus-analysis", "COURSE", "TEACHER"),
                step(3, "study-plan-generation", "COURSE"));
    }
    @Bean Workflow materialAnalysisWorkflow() {
        return workflow("material-analysis", LearningTaskType.MATERIAL_ANALYSIS,
                step(1, "material-summary", "MATERIALS"), step(2, "learning-result-synthesis", "COURSE"));
    }
    @Bean Workflow questionAssistanceWorkflow() {
        return workflow("question-assistance", LearningTaskType.QUESTION_ASSISTANCE, step(1, "progressive-hint"));
    }
    @Bean Workflow answerReviewWorkflow() {
        return workflow("answer-review", LearningTaskType.ANSWER_REVIEW, step(1, "solution-review"));
    }
    @Bean Workflow studyPlanningWorkflow() {
        return workflow("study-planning", LearningTaskType.STUDY_PLANNING, step(1, "study-plan-generation", "COURSE"));
    }
    private Workflow workflow(String id, LearningTaskType type, WorkflowStep... steps) {
        return new DefaultWorkflow(id, type, List.of(steps));
    }
    private WorkflowStep step(int order, String skillId, String... context) {
        return new WorkflowStep(order, skillId, Set.of(context));
    }
}
