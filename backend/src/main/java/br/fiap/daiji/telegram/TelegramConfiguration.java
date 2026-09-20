package br.fiap.daiji.telegram;

import br.fiap.daiji.assistant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class TelegramConfiguration {
    @Bean
    HttpTelegramClient telegramClient(@Value("${telegram.bot.token:}") String token, ObjectMapper mapper) {
        return new HttpTelegramClient(token, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), mapper);
    }
    @Bean
    TelegramUpdateReceiver telegramReceiver(MessageRouter router, ResponseFormatter formatter, TelegramClient client,
            org.springframework.beans.factory.ObjectProvider<BeneficiaryJourney> journey) {
        return new TelegramUpdateReceiver(router, formatter, client, journey.getIfAvailable());
    }
    @Bean
    TelegramPollingService telegramPolling(TelegramUpdateSource source, TelegramUpdateReceiver receiver,
            @Value("${telegram.bot.token:}") String token,
            @Value("${telegram.bot.poll-timeout-seconds:25}") int timeout) {
        boolean configured = token != null && token.matches("[0-9]+:[A-Za-z0-9_-]+")
                && token.length() <= 256;
        return new TelegramPollingService(source, receiver, Math.clamp(timeout, 1, 50), configured, Thread::sleep);
    }
}
