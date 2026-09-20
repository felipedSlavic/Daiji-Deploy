package br.fiap.daiji.telegram;

import br.fiap.daiji.assistant.*;
import br.fiap.daiji.gemini.GeminiClient;
import br.fiap.daiji.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class JourneyReceiverTest {
    @Test void entradaTelegramUsaChatSomenteParaEstadoENaoEnviaRespostasAoGemini() throws Exception {
        GeminiClient gemini = mock(GeminiClient.class); CheckinService service = mock(CheckinService.class);
        TelegramClient client = mock(TelegramClient.class); MessageRouter router = mock(MessageRouter.class);
        var confirmations = mock(ConfirmacaoMedicacaoService.class);
        try (var conversations = new CheckinConversations(service)) {
            var journey = new BeneficiaryJourney(conversations,new JourneyNaturalLanguage(Optional.of(gemini)),confirmations,router);
            var receiver = new TelegramUpdateReceiver(router,new ResponseFormatter(),client,journey);
            long chat = 9000000000L;
            for (String text : List.of("/checkin 1","3","BOA","BOA","CALMO","PULAR","CONFIRMAR"))
                receiver.receive(new TelegramUpdate(1L,new TelegramUpdate.Message(new TelegramUpdate.Chat(chat,"private"),text)));
            verify(service).validarBeneficiario(1); verify(service).criar(eq(1),any(),eq(br.fiap.daiji.model.CanalCheckin.TELEGRAM));
            verify(client).sendMessage(chat,"Check-in registrado com sucesso. ✅"); verifyNoInteractions(gemini,router,confirmations);
            receiver.receive(new TelegramUpdate(2L,new TelegramUpdate.Message(new TelegramUpdate.Chat(20L,"group"),"/checkin 2")));
            verify(service,never()).validarBeneficiario(2);
        }
    }
}
