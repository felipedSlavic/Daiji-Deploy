package br.fiap.daiji.telegram;

import br.fiap.daiji.assistant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class TelegramConfigurationTest {
    ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(TelegramConfiguration.class)
            .withBean(ObjectMapper.class,ObjectMapper::new)
            .withBean(MessageRouter.class,() -> new MessageRouter(List.of()))
            .withBean(ResponseFormatter.class,ResponseFormatter::new);
    @Test void disabledSemToken() {
        context.withPropertyValues("telegram.bot.enabled=false").run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(TelegramPollingService.class).doesNotHaveBean(TelegramClient.class);
        });
    }
    @Test void defaultDisabled() {
        context.run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(TelegramPollingService.class));
    }
    @Test void enabledSemTokenWarningNaoDerrubaSpring(CapturedOutput output) {
        context.withPropertyValues("telegram.bot.enabled=true").run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(TelegramPollingService.class);
            assertThat(c.getBean(TelegramPollingService.class).isRunning()).isFalse();
        });
        assertThat(output).contains("Telegram desativado");
    }
    @ParameterizedTest @ValueSource(strings = {"conteudo-sensivel", "https://interno/nao-imprimir", " "})
    void configuracaoInvalidaNaoVaza(String value, CapturedOutput output) {
        context.withPropertyValues("telegram.bot.enabled=true", "telegram.bot.token=" + value).run(c -> {
            assertThat(c).hasNotFailed(); assertThat(c.getBean(TelegramPollingService.class).isRunning()).isFalse();
        });
        if (!value.isBlank()) assertThat(output).doesNotContain(value);
    }
}
