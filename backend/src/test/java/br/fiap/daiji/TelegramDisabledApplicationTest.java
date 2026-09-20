package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.telegram.TelegramPollingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"telegram.bot.enabled=false", "telegram.bot.token="})
class TelegramDisabledApplicationTest {
    @Autowired ApplicationContext context;
    @MockitoBean ConnectionFactory factory;
    @Test void contextoCompletoIniciaSemTokenSemPollingSemOracle() {
        assertTrue(context.getBeansOfType(TelegramPollingService.class).isEmpty());
        verifyNoInteractions(factory);
    }
}
