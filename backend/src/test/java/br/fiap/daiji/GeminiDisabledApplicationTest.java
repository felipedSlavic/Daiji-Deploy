package br.fiap.daiji;

import br.fiap.daiji.assistant.*;
import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.gemini.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"gemini.enabled=false","gemini.api-key=","telegram.bot.enabled=false","telegram.bot.token="})
class GeminiDisabledApplicationTest {
    @Autowired ApplicationContext context;
    @Autowired MessageRouter router;
    @MockitoBean ConnectionFactory factory;
    @Test void contextoCompletoSemGeminiPreservaAjudaESaudacao() {
        assertTrue(context.getBeansOfType(GeminiClient.class).isEmpty());
        assertEquals(new AssistantResponse.Text(MessageRouter.AJUDA),router.route("/ajuda"));
        assertEquals(new AssistantResponse.Text(NaturalLanguageAssistant.HELP),router.route("oi"));
        verifyNoInteractions(factory);
    }
}
