package cn.finalscompass.ai.task;

import cn.finalscompass.ai.skill.AiSkillRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Closed-set, deterministic first-stage classifier for product learning tasks. */
@Component
public class LearningTaskRouter {
    private final AiSkillRegistry skills;

    public LearningTaskRouter(AiSkillRegistry skills) { this.skills = skills; }

    public LearningTask route(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isBlank()) throw new IllegalArgumentException("请输入需要完成的学习任务");
        String normalized = text.toLowerCase(Locale.ROOT);
        LearningTaskType type;
        Set<String> context;
        if (contains(normalized, "检查", "批改", "哪里错", "哪一步错", "proof review", "review my")) {
            type = LearningTaskType.ANSWER_REVIEW; context = Set.of();
        } else if (contains(normalized, "期末", "考试重点", "考试范围", "备考", "复习指南", "复习资料", "exam")) {
            type = LearningTaskType.EXAM_PREPARATION; context = Set.of("COURSE", "MATERIALS", "TEACHER");
        } else if (contains(normalized, "ppt", "讲义", "附件", "课程资料", "分析资料", "总结资料", "material")) {
            type = LearningTaskType.MATERIAL_ANALYSIS; context = Set.of("COURSE", "MATERIALS");
        } else if (contains(normalized, "学习计划", "复习计划", "怎么学", "安排学习", "study plan")) {
            type = LearningTaskType.STUDY_PLANNING; context = Set.of("COURSE");
        } else {
            type = LearningTaskType.QUESTION_ASSISTANCE; context = Set.of();
        }
        validateWorkflowSkills(type);
        return new LearningTask(UUID.randomUUID().toString(), type, text, context);
    }

    private boolean contains(String input, String... keywords) {
        for (String keyword : keywords) if (input.contains(keyword)) return true;
        return false;
    }

    private void validateWorkflowSkills(LearningTaskType type) {
        for (String id : switch (type) {
            case EXAM_PREPARATION -> new String[]{"material-summary", "exam-focus-analysis", "study-plan-generation"};
            case MATERIAL_ANALYSIS -> new String[]{"material-summary", "learning-result-synthesis"};
            case QUESTION_ASSISTANCE -> new String[]{"progressive-hint"};
            case ANSWER_REVIEW -> new String[]{"solution-review"};
            case STUDY_PLANNING -> new String[]{"study-plan-generation"};
        }) skills.require(id);
    }
}
