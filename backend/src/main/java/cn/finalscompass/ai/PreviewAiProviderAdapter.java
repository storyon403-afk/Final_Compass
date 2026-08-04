package cn.finalscompass.ai;

import java.util.Set;

/** V2 preview adapter that validates execution plans without sending external requests. */
public final class PreviewAiProviderAdapter implements AiProviderAdapter {
    private final String id;
    private final String displayName;
    private final Set<String> capabilities;

    public PreviewAiProviderAdapter(String id, String displayName, Set<String> capabilities) {
        this.id = id;
        this.displayName = displayName;
        this.capabilities = Set.copyOf(capabilities);
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public Set<String> capabilities() { return capabilities; }

    @Override
    public AiProviderResult invoke(AiProviderRequest request, char[] apiKey) {
        AiSkill skill = request.plan().primarySkill();
        skill.validate(request.plan().userInput());
        if (apiKey == null || apiKey.length < 8) throw new IllegalArgumentException("Provider 凭据不可用");
        String response = switch (skill.category()) {
            case "VISION" -> "已建立题目图片分析计划。真实 Provider 接入后将按题目转写、条件、目标和不确定区域输出。";
            case "LEARNING" -> "已由「" + skill.displayName() + "」生成学习型执行计划，正式模型将遵守渐进提示或解答审阅边界。";
            case "COURSE" -> request.plan().allowedTools().isEmpty()
                    ? "已生成资料整理计划。" : "已规划课程资料工具，但 Preview 模式不会伪装执行 MCP 或生成引用。";
            case "STATISTICS" -> "已生成统计方法选择计划，正式模型必须给出适用条件、检查步骤、备选方法和结论边界。";
            default -> "AI Skill 执行计划已通过。";
        };
        String content = displayName + " / " + request.model() + " · V2 Preview · " + response;
        return new AiProviderResult(content, request.plan().userInput().length(), content.length(), true);
    }
}
