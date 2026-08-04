package cn.finalscompass.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** Registers provider capabilities; preview adapters can later be replaced independently. */
@Configuration
public class AiProviderConfiguration {
    @Bean AiProviderAdapter openAiAdapter() {
        return new PreviewAiProviderAdapter("openai", "OpenAI / GPT", Set.of("TEXT", "IMAGE"));
    }

    @Bean AiProviderAdapter anthropicAdapter() {
        return new PreviewAiProviderAdapter("anthropic", "Anthropic / Claude", Set.of("TEXT", "IMAGE"));
    }

    @Bean AiProviderAdapter deepSeekAdapter() {
        return new PreviewAiProviderAdapter("deepseek", "DeepSeek", Set.of("TEXT"));
    }

    @Bean AiProviderAdapter geminiAdapter() {
        return new PreviewAiProviderAdapter("gemini", "Google / Gemini", Set.of("TEXT", "IMAGE"));
    }
}
