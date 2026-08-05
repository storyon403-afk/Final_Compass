package cn.finalscompass.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiVisionProblemPipelineTest {
    private final AiVisionProblemPipeline pipeline = new AiVisionProblemPipeline(null, null, null);

    @Test
    void userRequestControlsFinalLearningAction() {
        assertEquals("complete-solution", pipeline.targetSkill("请完整解题", "任务意图：分步提示"));
        assertEquals("progressive-hint", pipeline.targetSkill("只给我提示，不要答案", "一道导数题"));
        assertEquals("solution-review", pipeline.targetSkill("检查我的解答哪里错了", "含学生手写步骤"));
        assertEquals("concept-explanation", pipeline.targetSkill("解释为什么这样做", "一道概率题"));
    }

    @Test
    void defaultsToCompleteSolutionWhenIntentIsNotExplicit() {
        assertEquals("complete-solution", pipeline.targetSkill("帮我看看这道题", "题面已完整识别"));
    }
}
