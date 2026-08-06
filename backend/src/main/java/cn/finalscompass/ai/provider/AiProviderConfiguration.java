package cn.finalscompass.ai.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

/** Registers provider capabilities; preview adapters can later be replaced independently. */
@Configuration
public class AiProviderConfiguration {
    @Bean AiProviderAdapter openAiAdapter(ObjectMapper json) {
        return new OpenAiResponsesAdapter(json);
    }

    @Bean AiProviderAdapter deepSeekAdapter(ObjectMapper json) {
        return new DeepSeekProviderAdapter(json);
    }

    @Bean AiProviderAdapter geminiAdapter(ObjectMapper json) {
        return new GeminiGenerateContentAdapter(json);
    }

    @Bean AiProviderAdapter hermesAdapter(ObjectMapper json,
            @Value("${app.ai.hermes.url:http://127.0.0.1:8642}") String url) {
        return new HermesProviderAdapter(json, url);
    }
}
