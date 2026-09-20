package br.fiap.daiji.assistant;

import br.fiap.daiji.dto.*;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.service.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageRouterTest {
    BeneficiarioService pessoas = mock(BeneficiarioService.class);
    ScoreService scores = mock(ScoreService.class);
    MedicacaoService meds = mock(MedicacaoService.class);
    CheckinService checkins = mock(CheckinService.class);
    MessageRouter router = new MessageRouter(List.of(new ScoreCommandHandler(pessoas, scores),
            new MedicacoesCommandHandler(meds), new CheckinsCommandHandler(checkins)));
    ResponseFormatter formatter = new ResponseFormatter();
    String reply(String text) { return formatter.format(router.route(text)); }

    @BeforeEach void prepare() throws Exception {
        Beneficiario b = new Beneficiario(); b.setId(1); b.setNome("Rafael Almeida");
        b.setCpf("CPF-NAO-EXIBIR"); b.setEmail("EMAIL-NAO-EXIBIR");
        when(pessoas.buscarPorId(1)).thenReturn(Optional.of(b));
        when(scores.atual(1)).thenReturn(new ScoreResponse(1, 1, null, new BigDecimal("97.00"), "BAIXO", null));
    }
    @Test void start() {
        assertEquals("Olá! Eu sou o assistente Daiji.\n\nPosso consultar informações do acompanhamento do MVP.\n\n"
                + "Comandos disponíveis:\n/score <id>\n/medicacoes <id>\n/checkins <id>\n/ajuda\n\nExemplo:\n/score 1", reply("/start"));
        verifyNoInteractions(scores, meds, checkins);
    }
    @ParameterizedTest @ValueSource(strings = {"/ajuda", "/help", "  /AJUDA  ", "/help@DaijiBot"})
    void ajuda(String command) { assertEquals(MessageRouter.AJUDA, reply(command)); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"/outro", "Como estou hoje?", "   "})
    void desconhecido(String command) {
        assertEquals("Não entendi essa mensagem ainda.\n\nUse /ajuda para ver os comandos disponíveis.\n\n"
                + "Em breve o assistente Daiji também poderá responder perguntas em linguagem natural.", reply(command));
        verifyNoInteractions(scores, meds, checkins);
    }
    @ParameterizedTest @ValueSource(strings = {"/score 1", "  /SCORE   1  ", "/score@BotName 1", "/score\t1"})
    void scoreExatoSemRecalculo(String command) throws Exception {
        assertEquals("🩺 Score Daiji\n\nRafael Almeida\nScore atual: 97/100\nClassificação: BAIXO\n\n" + MessageRouter.AVISO, reply(command));
        verify(scores).atual(1); verify(scores, never()).recalcular(any()); verifyNoMoreInteractions(scores);
    }
    @ParameterizedTest @CsvSource({"97,BAIXO", "55,MEDIO", "20,ALTO"})
    void scoreEstruturado(int valor, String risco) throws Exception {
        when(scores.atual(1)).thenReturn(new ScoreResponse(1,1,null, BigDecimal.valueOf(valor), risco,null));
        var result = assertInstanceOf(AssistantResponse.Score.class, router.route("/score 1"));
        assertEquals("Rafael Almeida", result.nome()); assertEquals(risco, result.classificacao());
        assertEquals(BigDecimal.valueOf(valor), result.valor());
        assertFalse(reply("/score 1").contains("NAO-EXIBIR"));
    }
    @Test void semScore() throws Exception {
        when(scores.atual(1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals("Ainda não há score calculado para este beneficiário.", reply("/score 1"));
    }
    @Test void scoreBeneficiarioAusente() {
        assertEquals("Beneficiário não encontrado.", reply("/score 2")); verifyNoInteractions(scores);
    }
    @Test void exclusaoConcorrente() throws Exception {
        var pessoa = pessoas.buscarPorId(1);
        when(pessoas.buscarPorId(1)).thenReturn(pessoa, Optional.empty());
        when(scores.atual(1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals("Beneficiário não encontrado.", reply("/score 1"));
    }
    @ParameterizedTest @ValueSource(strings = {"/score", "/score abc", "/score -1", "/score 0", "/score 2147483648", "/score 1 2",
            "/medicacoes", "/medicacoes abc", "/medicacoes -1", "/checkins", "/checkins xyz", "/checkins 0", "/score +1", "/score 1.5"})
    void idsInvalidos(String command) {
        String base = command.split(" ")[0];
        assertEquals("Formato inválido.\nUse: " + base + " <idBeneficiario>\nExemplo: " + base + " 1", reply(command));
        verifyNoInteractions(scores, meds, checkins);
    }
    @Test void medicacoes() throws Exception {
        when(meds.listar(1)).thenReturn(List.of(new MedicacaoResponse(2,1,"Losartana","50 mg","08:00"),
                new MedicacaoResponse(3,1,"Medicamento X","10 mg","20:00")));
        assertEquals("💊 Medicações cadastradas\n\n• Losartana — 50 mg — 08:00\n• Medicamento X — 10 mg — 20:00", reply("/medicacoes 1"));
        verify(meds).listar(1); verifyNoMoreInteractions(meds);
    }
    @Test void medicacaoNull() throws Exception {
        when(meds.listar(1)).thenReturn(List.of(new MedicacaoResponse(2,1,"X",null,null)));
        assertEquals("💊 Medicações cadastradas\n\n• X — Não informado — Não informado", reply("/medicacoes 1"));
    }
    @Test void medicacoesVazias() { assertEquals("Nenhuma medicação cadastrada.", reply("/medicacoes 1")); }
    @Test void checkinsVazios() { assertEquals("Nenhum check-in registrado.", reply("/checkins 1")); }
    @Test void checkinsCampos() throws Exception {
        when(checkins.recentes(1)).thenReturn(List.of(new CheckinResumo(LocalDateTime.of(2026,9,16,10,30),3,"BOA","BOA","CALMO")));
        assertEquals("📋 Check-ins recentes\n\n16/09/2026 10:30\nEstresse: 3/5\nSono: BOA\nAlimentação: BOA\nHumor: CALMO", reply("/checkins 1"));
        verify(checkins).recentes(1); verifyNoMoreInteractions(checkins);
    }
    @Test void checkinsNull() throws Exception {
        when(checkins.recentes(1)).thenReturn(List.of(new CheckinResumo(null,null,null,null,null)));
        assertEquals("📋 Check-ins recentes\n\nNão informado\nEstresse: Não informado\nSono: Não informado\nAlimentação: Não informado\nHumor: Não informado", reply("/checkins 1"));
    }
    @ParameterizedTest @ValueSource(strings = {"/medicacoes", "/checkins"})
    void beneficiarioAusente(String command) throws Exception {
        when(meds.listar(1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "interno"));
        when(checkins.recentes(1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "interno"));
        assertEquals("Beneficiário não encontrado.", reply(command + " 1"));
    }
    @ParameterizedTest @ValueSource(strings = {"/score", "/medicacoes", "/checkins"})
    void erroNaoVaza(String command) throws Exception {
        when(scores.atual(1)).thenThrow(new SQLException("ORA-999 SQL JDBC credenciais"));
        when(meds.listar(1)).thenThrow(new SQLException("ORA-999 SQL JDBC credenciais"));
        when(checkins.recentes(1)).thenThrow(new IllegalStateException("interno"));
        assertEquals(MessageRouter.ERRO, reply(command + " 1"));
    }
}
