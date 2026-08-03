package cn.finalscompass.ai;

import java.util.Set;

/** V1 adapter that validates the complete call path without sending requests to a real provider. */
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
        request.skill().validate(request.input());
        if (apiKey == null || apiKey.length < 8) throw new IllegalArgumentException("Provider 凭据不可用");
        String response = switch (request.skill().category()) {
            case "VISION" -> "图片分析 Skill 的文本预检已通过；V1 已验证 Provider 与凭据通道，真实图片上传将在后续版本接入。";
            case "LEARNING" -> "学习 Skill 预检已通过；将由「" + request.skill().displayName() + "」约束后续模型的学习型输出。";
            case "COURSE" -> "课程 Skill 预检已通过；V1 不读取课程资料，后续接入 MCP MaterialTools 后才生成带引用回答。";
            case "STATISTICS" -> "统计 Skill 预检已通过；后续模型输出必须包含推荐方法、适用条件和备选方案。";
            default -> "AI Skill 预检已通过。";
        };
        String content = displayName + " / " + request.model() + " · " + response;
        return new AiProviderResult(content, request.input().length(), content.length(), true);
    }
}
