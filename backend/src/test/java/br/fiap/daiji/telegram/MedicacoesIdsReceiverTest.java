package br.fiap.daiji.telegram;

import br.fiap.daiji.factory.ConnectionFactory;
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
        }
    }
    void receive(String text) throws Exception {
        receiver.receive(new TelegramUpdate(10L,new TelegramUpdate.Message(new TelegramUpdate.Chat(9000000000L,"private"),text)));
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids 1", "/medicacoes\\_ids 1", "  /MEDICACOES_IDS@DaijiBot   1  ", "/medicacoes\\_ids@DaijiBot 1"})
    void caminhoRealAteSqlMostraIdsESomenteMedicacoesDoBeneficiario(String text) throws Exception {
        receive(text);
        verify(client).sendMessage(9000000000L,"💊 Medicações cadastradas (IDs)\n\n• ID 1 — Medicamento A — 10 mg — 08:00");
        verifyNoMoreInteractions(client);
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids 2", "/medicacoes\\_ids 2"})
    void outroBeneficiarioCamposOpcionais(String text) throws Exception {
        receive(text);
        verify(client).sendMessage(9000000000L,"💊 Medicações cadastradas (IDs)\n\n• ID 2 — Medicamento B — Não informado — Não informado");
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids 999", "/medicacoes\\_ids 999"})
    void inexistente(String text) throws Exception {
        receive(text); verify(client).sendMessage(9000000000L,"Beneficiário não encontrado.");
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids", "/medicacoes_ids abc", "/medicacoes_ids 0", "/medicacoes_ids -1", "/medicacoes\\_ids abc"})
    void invalidoOrientaUso(String text) throws Exception {
        receive(text);
        verify(client).sendMessage(9000000000L,"Formato inválido.\nUse: /medicacoes_ids <idBeneficiario>\nExemplo: /medicacoes_ids 1");
        verifyNoInteractions(factory);
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes_ids 3", "/medicacoes\\_ids 3"})
    void listaVazia(String text) throws Exception {
        receive(text); verify(client).sendMessage(9000000000L,"Nenhuma medicação cadastrada.");
    }
}
