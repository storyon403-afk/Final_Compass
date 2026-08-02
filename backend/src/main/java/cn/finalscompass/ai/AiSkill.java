package cn.finalscompass.ai;

public interface AiSkill {
    String id();
    String displayName();
    String description();
    int maxInputLength();

    default void validate(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("请输入需要分析的内容");
        if (input.length() > maxInputLength()) throw new IllegalArgumentException("输入内容超过 Skill 限制");
    }
}
