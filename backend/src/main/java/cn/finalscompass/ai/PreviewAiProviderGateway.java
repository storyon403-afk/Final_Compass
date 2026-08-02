package cn.finalscompass.ai;

import org.springframework.stereotype.Component;

@Component
public class PreviewAiProviderGateway implements AiProviderGateway {
    @Override
    public AiProviderResult invoke(AiProviderRequest request, char[] apiKey) {
        request.skill().validate(request.input());
        String response = "AI 分析通道已完成安全预检。当前分支尚未连接真实模型；后续可在 AiProviderGateway 实现中接入 "
                + request.provider() + "，并由 Skill「" + request.skill().displayName() + "」约束输入与输出。";
        return new AiProviderResult(response, request.input().length(), response.length());
    }
}
