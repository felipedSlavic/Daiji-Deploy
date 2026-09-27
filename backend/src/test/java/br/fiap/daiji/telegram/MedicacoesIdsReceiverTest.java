package br.fiap.daiji.telegram;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.assistant.BeneficiaryJourney;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;

/** Beans reais do receiver ao DAO; apenas transporte externo e conexão Oracle substituídos. */
@SpringBootTest(properties = {"telegram.bot.enabled=true", "telegram.bot.token=", "gemini.enabled=false"})
class MedicacoesIdsReceiverTest {
    @Autowired TelegramUpdateReceiver receiver;
    @MockitoBean HttpTelegramClient client;
    @MockitoBean ConnectionFactory factory;
    private static final String DB = "jdbc:h2:mem:medicacoes_ids_receiver;MODE=Oracle;DB_CLOSE_DELAY=-1";

    @BeforeEach void prepare() throws Exception {
        when(factory.conectar()).thenAnswer(call -> DriverManager.getConnection(DB));
        try (Connection c = DriverManager.getConnection(DB); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            for (String path : new String[]{"/cp06-fixture.sql", "/cp09-fixture.sql"}) {
                try (var reader = new InputStreamReader(getClass().getResourceAsStream(path), StandardCharsets.UTF_8)) {
                    RunScript.execute(c,reader);
                }
            }
            s.execute("UPDATE BENEFICIARIO SET email = 'bruno@daiji.com' WHERE id_beneficiario = 3");
        }
    }
    void receive(String text) throws Exception {
        receiver.receive(new TelegramUpdate(10L,new TelegramUpdate.Message(new TelegramUpdate.Chat(9000000000L,"private"),text)));
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids rafael@daiji.com", "/medicacoes\\_ids rafael@daiji.com", "  /MEDICACOES_IDS@DaijiBot   rafael@daiji.com  ", "/medicacoes\\_ids@DaijiBot rafael@daiji.com"})
    void caminhoRealAteSqlMostraIdsESomenteMedicacoesDoBeneficiario(String text) throws Exception {
        receive(text);
        verify(client).sendMessage(9000000000L,"💊 Medicações cadastradas (IDs)\n\n• ID 1 — Medicamento A — 10 mg — 08:00");
        verifyNoMoreInteractions(client);
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids maria@daiji.com", "/medicacoes\\_ids maria@daiji.com"})
    void outroBeneficiarioCamposOpcionais(String text) throws Exception {
        receive(text);
        verify(client).sendMessage(9000000000L,"💊 Medicações cadastradas (IDs)\n\n• ID 2 — Medicamento B — Não informado — Não informado");
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids ausente@daiji.com", "/medicacoes\\_ids ausente@daiji.com"})
    void inexistente(String text) throws Exception {
        receive(text); verify(client).sendMessage(9000000000L,BeneficiaryJourney.EMAIL_NOT_FOUND);
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids", "/medicacoes_ids abc", "/medicacoes_ids 0", "/medicacoes_ids -1", "/medicacoes\\_ids abc"})
    void invalidoOrientaUso(String text) throws Exception {
        receive(text);
        verify(client).sendMessage(9000000000L,text.equals("/medicacoes_ids") ? BeneficiaryJourney.ASK_EMAIL : BeneficiaryJourney.INVALID_EMAIL);
        verifyNoInteractions(factory);
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids bruno@daiji.com", "/medicacoes\\_ids bruno@daiji.com"})
    void listaVazia(String text) throws Exception {
        receive(text); verify(client).sendMessage(9000000000L,"Nenhuma medicação cadastrada.");
    }
}
