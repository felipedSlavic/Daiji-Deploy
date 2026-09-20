package br.fiap.daiji.gemini;

import br.fiap.daiji.assistant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GeminiConfigurationTest {
    ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(GeminiConfiguration.class,NaturalLanguageAssistant.class)
            .withBean(ObjectMapper.class,ObjectMapper::new);
    @Test void disabled() {
        context.withPropertyValues("gemini.enabled=false").run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(GeminiClient.class);
            assertThat(c.getBean(NaturalLanguageAssistant.class).answer("Score do ID 1"))
                    .isEqualTo(new AssistantResponse.Text(NaturalLanguageAssistant.FALLBACK));
        });
    }
    @Test void defaultDisabled() { context.run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(GeminiClient.class)); }
    @Test void enabledSemChaveNaoDerrubaSpring(CapturedOutput output) {
        context.withPropertyValues("gemini.enabled=true","gemini.api-key=","gemini.model=modelo-configuravel").run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(NaturalLanguageAssistant.class).answer("Score do ID 1"))
                    .isEqualTo(new AssistantResponse.Text(NaturalLanguageAssistant.FALLBACK));
        });
        assertThat(output).contains("Gemini desativado").doesNotContain("modelo-configuravel");
    }
    @Test void modeloInvalidoNaoVaza(CapturedOutput output) {
        context.withPropertyValues("gemini.enabled=true","gemini.api-key=","gemini.model=https://nao-imprimir").run(c -> assertThat(c).hasNotFailed());
        assertThat(output).doesNotContain("https://nao-imprimir");
    }
}
