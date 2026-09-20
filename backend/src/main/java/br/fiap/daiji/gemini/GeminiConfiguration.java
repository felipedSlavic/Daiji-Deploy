package br.fiap.daiji.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "gemini.enabled", havingValue = "true")
public class GeminiConfiguration {
    @Bean
    GeminiClient geminiClient(ObjectMapper mapper, @Value("${gemini.api-key:}") String key,
            @Value("${gemini.model:}") String model, @Value("${gemini.timeout-seconds:12}") int seconds) {
        if (key == null || key.isBlank() || !key.matches("[\\x21-\\x7E]{1,256}")
                || model == null || !model.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}")) {
            LoggerFactory.getLogger(GeminiConfiguration.class).warn("Gemini desativado: confira GEMINI_API_KEY e GEMINI_MODEL no ambiente.");
            return null;
        }
        return new HttpGeminiClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), mapper, key, model,
                Duration.ofSeconds(Math.clamp(seconds, 1, 30)), Clock.systemUTC());
    }
}
