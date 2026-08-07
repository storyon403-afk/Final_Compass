package cn.finalscompass.ai;

import cn.finalscompass.ai.skill.AiSkillRegistry;
import cn.finalscompass.ai.skill.DefaultAiSkill;
import cn.finalscompass.ai.skill.AiSkill;
import cn.finalscompass.ai.task.LearningTaskRouter;
import cn.finalscompass.ai.task.LearningTaskType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LearningTaskRouterTest {
    private LearningTaskRouter router() {
        var ids = Set.of("material-summary", "exam-focus-analysis", "study-plan-generation",
                "learning-result-synthesis", "progressive-hint", "solution-review");
        var skills = ids.stream().<AiSkill>map(id -> new DefaultAiSkill(id, "TEST", id, id, 1000,
                Set.of("TEXT"), id, id, Set.<String>of())).toList();
        return new LearningTaskRouter(new AiSkillRegistry(skills));
    }

    @Test void recognizesClosedSetProductTasks() {
        var router = router();
        assertEquals(LearningTaskType.EXAM_PREPARATION, router.route("帮我准备高数期末考试").taskType());
        assertEquals(LearningTaskType.MATERIAL_ANALYSIS, router.route("分析一下我上传的课程PPT").taskType());
        assertEquals(LearningTaskType.ANSWER_REVIEW, router.route("检查我的证明哪里错了").taskType());
        assertEquals(LearningTaskType.STUDY_PLANNING, router.route("给我安排学习计划").taskType());
        assertEquals(LearningTaskType.QUESTION_ASSISTANCE, router.route("这道题我不会").taskType());
    }

    @Test void cannotCreateTaskTypesOutsideEnum() {
        assertTrue(Arrays.asList(LearningTaskType.values()).contains(router().route("随便问问").taskType()));
        assertThrows(IllegalArgumentException.class, () -> router().route("  "));
    }
}
