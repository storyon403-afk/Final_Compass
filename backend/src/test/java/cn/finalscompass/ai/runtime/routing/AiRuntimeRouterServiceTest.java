package cn.finalscompass.ai.runtime.routing;

import cn.finalscompass.ai.runtime.trace.RuntimeType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRuntimeRouterServiceTest {
    private final AiRuntimeRouterService router = new AiRuntimeRouterService();

    @Test void routesFileGenerationTaskToAgent() {
        var result = router.route(new AiRuntimeRouterService.RouteRequest("生成期末复习 PDF", "AUTO", Set.of()));
        assertEquals(RuntimeType.AGENT, result.runtimeType());
        assertEquals("AGENT_GATEWAY", result.runtimeDefinitionKey());
    }

    @Test void routesPlainQuestionToChat() {
        var result = router.route(new AiRuntimeRouterService.RouteRequest("期末考试一般怎么安排复习节奏？", "AUTO", Set.of()));
        assertEquals(RuntimeType.CHAT, result.runtimeType());
        assertEquals("CHAT", result.runtimeDefinitionKey());
    }

    @Test void routesAutonomousResearchToAgent() {
        var result = router.route(new AiRuntimeRouterService.RouteRequest("自主调研并拆解这个开放任务", "AUTO", Set.of()));
        assertEquals(RuntimeType.AGENT, result.runtimeType());
        assertTrue(result.requiredClientCapabilities().contains("EXTERNAL_AGENT_GATEWAY"));
    }

    @Test void webAgentRequiresChromeExtensionAndFallsBackSafely() {
        var result = router.route(new AiRuntimeRouterService.RouteRequest("让 Kimi 和 Qwen 多网页协作", "AUTO", Set.of()));
        assertEquals(RuntimeType.AGENT, result.runtimeType());
        assertEquals("AGENT_FALLBACK", result.runtimeDefinitionKey());
        assertTrue(result.requiredClientCapabilities().contains("CHROME_EXTENSION"));
    }
}
