package cn.finalscompass.ai;

import org.springframework.stereotype.Component;

@Component
public class StudyAnalysisSkill implements AiSkill {
    @Override public String id() { return "study-analysis-preview"; }
    @Override public String displayName() { return "学习分析 · 预览"; }
    @Override public String description() { return "为后续复习总结、考点提取与学习规划 Skill 预留的安全入口。"; }
    @Override public int maxInputLength() { return 8000; }
}
