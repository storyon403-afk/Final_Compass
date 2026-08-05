package cn.finalscompass.ai;

import cn.finalscompass.ai.agent.AiSkillPlanner;
import cn.finalscompass.ai.credential.AiCredentialSource;
import cn.finalscompass.ai.credential.ResolvedAiCredential;
import cn.finalscompass.ai.provider.AiProviderAdapter;
import cn.finalscompass.ai.provider.AiProviderGateway;
import cn.finalscompass.ai.provider.PreviewAiProviderAdapter;
import cn.finalscompass.ai.skill.AiSkill;
import cn.finalscompass.ai.skill.AiSkillRegistry;
import cn.finalscompass.ai.skill.DefaultAiSkill;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AiV1ArchitectureTest {
    @Test
    void registryExposesSkillContractAndRejectsUnknownSkill() {
        AiSkill skill = new DefaultAiSkill("progressive-hint", "LEARNING", "分步提示",
                "逐层提示", 100, Set.of("TEXT"), "逐层引导", "给出一级提示", Set.of());
        AiSkillRegistry registry = new AiSkillRegistry(List.of(skill));

        assertEquals("LEARNING", registry.available().getFirst().category());
        assertEquals(Set.of("TEXT"), registry.available().getFirst().modalities());
        assertSame(skill, registry.require("progressive-hint"));
        assertThrows(IllegalArgumentException.class, () -> registry.require("missing"));
    }

    @Test
    void providerGatewayRoutesAdapterAndChecksVisionCapability() {
        AiProviderAdapter textOnly = new PreviewAiProviderAdapter("deepseek", "DeepSeek", Set.of("TEXT"));
        AiProviderAdapter multimodal = new PreviewAiProviderAdapter("gemini", "Gemini", Set.of("TEXT", "IMAGE"));
        AiProviderGateway gateway = new AiProviderGateway(List.of(textOnly, multimodal));
        AiSkill vision = new DefaultAiSkill("math-problem-image-analysis", "VISION", "题目图片分析",
                "识别题目", 100, Set.of("TEXT", "IMAGE"), "识别题目", "结构化输出", Set.of());
        var plan = new AiSkillPlanner.ExecutionPlan(vision, "EXPLICIT", "识别题目", "题目",
                Set.of(), List.of(), List.of(vision.id()));
        char[] key = "test-key-123".toCharArray();

        assertThrows(IllegalArgumentException.class,
                () -> gateway.invoke("deepseek", "preview", plan, key));
        var result = gateway.invoke("gemini", "preview", plan, key);
        assertTrue(result.preview());
        assertTrue(result.content().contains("Gemini"));
    }

    @Test
    void resolvedCredentialClearsMutableKeyOnClose() {
        char[] key = "secret-key".toCharArray();
        ResolvedAiCredential credential = new ResolvedAiCredential(
                "openai", "user-selected", AiCredentialSource.EPHEMERAL_BYOK, key);

        credential.close();

        for (char value : key) assertEquals('\0', value);
    }
}
