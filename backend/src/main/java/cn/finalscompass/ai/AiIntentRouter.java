package cn.finalscompass.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Rule-based V2 router; deterministic now and replaceable by a model router later. */
@Component
public class AiIntentRouter {
    private static final List<Rule> RULES = List.of(
            new Rule("solution-review", List.of("检查解答", "哪里错", "哪一步错", "批改", "验算", "review my solution")),
            new Rule("statistics-method-selector", List.of("统计方法", "用什么检验", "t检验", "卡方", "回归方法", "anova", "hypothesis test")),
            new Rule("concept-explanation", List.of("什么是", "解释概念", "区别是什么", "如何理解", "概念", "what is")),
            new Rule("course-question-answering", List.of("这门课", "课程资料", "老师要求", "课程考试", "course material")),
            new Rule("material-summary", List.of("总结附件", "总结资料", "提炼", "复习提纲", "summary")),
            new Rule("math-problem-image-analysis", List.of("识别图片", "题目图片", "看图", "手写题"))
    );

    public RoutingDecision route(String requestedSkillId, String input) {
        if (requestedSkillId != null && !requestedSkillId.isBlank() && !"auto".equalsIgnoreCase(requestedSkillId)) {
            return new RoutingDecision(requestedSkillId, "EXPLICIT");
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        return RULES.stream().filter(rule -> rule.keywords().stream().anyMatch(normalized::contains)).findFirst()
                .map(rule -> new RoutingDecision(rule.skillId(), "RULE_MATCH"))
                .orElse(new RoutingDecision("progressive-hint", "DEFAULT_LEARNING"));
    }

    private record Rule(String skillId, List<String> keywords) {}
    public record RoutingDecision(String skillId, String reasonCode) {}
}
