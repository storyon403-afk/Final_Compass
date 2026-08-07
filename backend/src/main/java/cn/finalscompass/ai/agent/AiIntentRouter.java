package cn.finalscompass.ai.agent;


import cn.finalscompass.ai.agent.intent.Difficulty;
import cn.finalscompass.ai.agent.intent.IntentDecision;
import cn.finalscompass.ai.agent.intent.IntentType;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Agent Intent Router
 *
 * 负责：
 *
 * 用户输入
 *      |
 *      v
 * IntentDecision
 *
 *
 * 注意：
 *
 * 不负责选择 Skill。
 * 不负责执行。
 *
 * Skill 映射由 AiSkillPlanner 完成。
 */
@Component
public class AiIntentRouter {



    private static final List<Rule> RULES = List.of(


            new Rule(
                    IntentType.REVIEW,
                    List.of(
                            "检查解答",
                            "哪里错",
                            "哪一步错",
                            "批改",
                            "验算",
                            "review"
                    )
            ),



            new Rule(
                    IntentType.PROOF,
                    List.of(
                            "证明",
                            "证明一下",
                            "prove",
                            "推导证明"
                    )
            ),



            new Rule(
                    IntentType.SOLUTION,
                    List.of(
                            "解题",
                            "完整解答",
                            "直接解答",
                            "求解",
                            "solve",
                            "计算"
                    )
            ),



            new Rule(
                    IntentType.STATISTICS,
                    List.of(
                            "统计方法",
                            "t检验",
                            "卡方",
                            "回归方法",
                            "anova",
                            "假设检验"
                    )
            ),



            new Rule(
                    IntentType.EXPLANATION,
                    List.of(
                            "什么是",
                            "解释概念",
                            "区别是什么",
                            "如何理解",
                            "概念"
                    )
            ),



            new Rule(
                    IntentType.COURSE_QA,
                    List.of(
                            "课程资料",
                            "老师要求",
                            "考试范围",
                            "复习范围"
                    )
            ),



            new Rule(
                    IntentType.SUMMARY,
                    List.of(
                            "总结附件",
                            "总结资料",
                            "复习提纲",
                            "整理笔记"
                    )
            )

    );




    /**
     * 主路由入口
     *
     * @param requestedSkillId 用户主动指定模式(可为空)
     * @param input 用户输入
     */
    public IntentDecision route(
            String requestedSkillId,
            String input
    ) {



        /*
         * 用户显式选择优先
         *
         * 例如：
         * 前端选择：
         * math-proof
         *
         */
        if (requestedSkillId != null
                && !requestedSkillId.isBlank()
                && !"auto".equalsIgnoreCase(requestedSkillId.trim())) {

            return fromRequestedSkill(
                    requestedSkillId
            );

        }




        if(input == null
                ||
           input.isBlank()
        ){

            return unknown(
                    "EMPTY_INPUT"
            );

        }




        String normalized =
                input.toLowerCase(Locale.ROOT);




        return RULES.stream()

                .filter(rule ->
                        rule.keywords()
                                .stream()
                                .anyMatch(
                                        normalized::contains
                                )
                )

                .findFirst()

                .map(rule ->

                        new IntentDecision(

                                rule.type(),

                                extractSubject(input),

                                Difficulty.UNDERGRADUATE,

                                0.85,

                                false,

                                "RULE_MATCH"

                        )

                )

                .orElse(

                        unknown(
                                "NO_RULE_MATCH"
                        )

                );

    }






    /**
     * 用户指定 Skill 时转换为 Intent
     *
     * 注意：
     *
     * 这里仍然不是执行 Skill。
     *
     * 只是把外部请求转换成 Intent。
     */
    private IntentDecision fromRequestedSkill(
            String skillId
    ){


        return switch(skillId){


            case "math-proof-solver" ->

                    new IntentDecision(
                            IntentType.PROOF,
                            null,
                            Difficulty.UNDERGRADUATE,
                            1.0,
                            false,
                            "EXPLICIT_SKILL"
                    );



            case "math-solution-solver", "complete-solution" ->

                    new IntentDecision(
                            IntentType.SOLUTION,
                            null,
                            Difficulty.UNDERGRADUATE,
                            1.0,
                            false,
                            "EXPLICIT_SKILL"
                    );



            case "solution-review" ->

                    new IntentDecision(
                            IntentType.REVIEW,
                            null,
                            Difficulty.UNDERGRADUATE,
                            1.0,
                            false,
                            "EXPLICIT_SKILL"
                    );



            case "concept-explanation" ->

                    new IntentDecision(
                            IntentType.EXPLANATION,
                            null,
                            Difficulty.BASIC,
                            1.0,
                            false,
                            "EXPLICIT_SKILL"
                    );

            case "progressive-hint" ->
                    new IntentDecision(IntentType.UNKNOWN, null, Difficulty.BASIC, 1.0, false, "EXPLICIT_SKILL");
            case "statistics-method-selector" ->
                    new IntentDecision(IntentType.STATISTICS, null, Difficulty.UNDERGRADUATE, 1.0, false, "EXPLICIT_SKILL");
            case "course-question-answering" ->
                    new IntentDecision(IntentType.COURSE_QA, null, Difficulty.UNDERGRADUATE, 1.0, false, "EXPLICIT_SKILL");
            case "material-summary" ->
                    new IntentDecision(IntentType.SUMMARY, null, Difficulty.BASIC, 1.0, false, "EXPLICIT_SKILL");
            case "math-problem-image-analysis" ->
                    new IntentDecision(IntentType.SOLUTION, null, Difficulty.UNDERGRADUATE, 1.0, true, "EXPLICIT_SKILL");



            default ->

                    unknown(
                            "UNKNOWN_REQUESTED_SKILL"
                    );

        };

    }






    private IntentDecision unknown(
            String reason
    ){

        return new IntentDecision(

                IntentType.UNKNOWN,

                "unknown",

                Difficulty.BASIC,

                0.0,

                false,

                reason

        );

    }


    /**
     * 当前简单版本。
     *
     * 后续可以升级：
     *
     * NLP Entity Extractor
     * LLM Router
     *
     */
    private String extractSubject(
            String input
    ){

        return null;

    }






    private record Rule(

            IntentType type,

            List<String> keywords

    ){}



}
