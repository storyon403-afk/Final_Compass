package cn.finalscompass.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

/** Registers provider capabilities; preview adapters can later be replaced independently. */
@Configuration
public class AiProviderConfiguration {
    @Bean AiProviderAdapter openAiAdapter(ObjectMapper json) {
        return new OpenAiResponsesAdapter(json);
    }

    @Bean AiProviderAdapter anthropicAdapter() {
        return new PreviewAiProviderAdapter("anthropic", "Anthropic / Claude", Set.of("TEXT", "IMAGE"));
    }

    @Bean AiProviderAdapter deepSeekAdapter(ObjectMapper json) {
        return new DeepSeekProviderAdapter(json);
    }

    @Bean AiProviderAdapter geminiAdapter() {
        return new PreviewAiProviderAdapter("gemini", "Google / Gemini", Set.of("TEXT", "IMAGE"));
    }
}
