package cn.finalscompass.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** Registers the product Skills exposed by the V1 AI analysis module. */
@Configuration
public class AiSkillConfiguration {
    @Bean AiSkill mathProblemImageAnalysis() {
        return skill("math-problem-image-analysis", "VISION", "题目图片分析",
                "识别数学题图片中的文字、公式、条件与求解目标，并标记无法确定的区域。", 12000, "TEXT", "IMAGE");
    }

    @Bean AiSkill progressiveHint() {
        return skill("progressive-hint", "LEARNING", "分步提示",
                "按知识点、关键公式和解题步骤逐层提示，避免一开始直接给出完整答案。", 8000, "TEXT");
    }

    @Bean AiSkill solutionReview() {
        return skill("solution-review", "LEARNING", "解答检查",
                "检查用户的演算过程，定位第一处错误并给出修正方向。", 12000, "TEXT", "IMAGE");
    }

    @Bean AiSkill conceptExplanation() {
        return skill("concept-explanation", "LEARNING", "概念解释",
                "使用定义、直观理解和小例子解释数学或统计学概念。", 8000, "TEXT");
    }

    @Bean AiSkill courseQuestionAnswering() {
        return skill("course-question-answering", "COURSE", "课程资料问答",
                "为后续基于已审核课程资料和可验证引用的问答能力提供统一入口。", 10000, "TEXT");
    }

    @Bean AiSkill materialSummary() {
        return skill("material-summary", "COURSE", "资料摘要",
                "提炼资料结构、核心知识点、公式和复习顺序。", 16000, "TEXT");
    }

    @Bean AiSkill statisticsMethodSelector() {
        return skill("statistics-method-selector", "STATISTICS", "统计方法选择",
                "根据研究问题、变量类型与假设条件推荐统计方法，并说明适用条件。", 8000, "TEXT");
    }

    private AiSkill skill(String id, String category, String name, String description,
                          int maxInputLength, String... modalities) {
        return new DefaultAiSkill(id, category, name, description, maxInputLength, Set.of(modalities));
    }
}
