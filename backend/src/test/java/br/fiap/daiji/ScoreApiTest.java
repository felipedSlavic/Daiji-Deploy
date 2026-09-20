package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import java.math.BigDecimal;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ScoreApiTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        br.fiap.daiji.service.ScoreService scoreFixo(br.fiap.daiji.dao.BeneficiarioDAO beneficiarios,
                                                    br.fiap.daiji.dao.ScoreDAO scores) {
            return new br.fiap.daiji.service.ScoreService(beneficiarios, scores,
                java.time.Clock.fixed(java.time.Instant.parse("2026-09-16T15:30:45Z"), java.time.ZoneId.of("America/Sao_Paulo")));
        }
    }
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionFactory factory;
    Connection connection;
    PreparedStatement pessoaPs, comorbidadesPs, adesaoPs, checkinsPs, insert, atualPs;
    ResultSet pessoa, comorbidades, adesao, checkins, keys, atual;
    static final String URL = "/api/beneficiarios/7/score";

    PreparedStatement consulta(String trecho, ResultSet rs) throws Exception {
        var ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains(trecho))).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        return ps;
    }

    @BeforeEach void preparar() throws Exception {
        connection = mock(Connection.class); when(factory.conectar()).thenReturn(connection);
        pessoa = mock(ResultSet.class); comorbidades = mock(ResultSet.class); adesao = mock(ResultSet.class);
        checkins = mock(ResultSet.class); keys = mock(ResultSet.class); atual = mock(ResultSet.class);
        pessoaPs = consulta("FROM BENEFICIARIO", pessoa);
        comorbidadesPs = consulta("FROM COMORBIDADE c", comorbidades);
        adesaoPs = consulta("FROM CONFIRMACAO_MEDICACAO cm", adesao);
        checkinsPs = consulta("FROM CHECKIN WHERE", checkins);
        atualPs = consulta("FROM SCORE_RISCO_RENAL WHERE", atual);
        when(pessoa.next()).thenReturn(true);
        when(pessoa.getInt("id_beneficiario")).thenReturn(7);
        when(pessoa.getDate("data_nascimento")).thenReturn(Date.valueOf("2000-01-02"));
        when(pessoa.getDate("data_cadastro")).thenReturn(Date.valueOf("2026-09-15"));
        insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(insert);
        when(insert.executeUpdate()).thenReturn(1); when(insert.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true); when(keys.getLong(1)).thenReturn(42L);
    }

    @Test void postCriaHistoricoIdentityEFatoresSemBody() throws Exception {
        mvc.perform(post(URL + "/recalcular")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.idScore").value(42)).andExpect(jsonPath("$.idBeneficiario").value(7))
            .andExpect(jsonPath("$.valorScore").value(100)).andExpect(jsonPath("$.classificacaoRisco").value("BAIXO"))
            .andExpect(jsonPath("$.dataCalculo").isString())
            .andExpect(jsonPath("$.fatores.hipertensao").value(0)).andExpect(jsonPath("$.fatores.diabetes").value(0))
            .andExpect(jsonPath("$.fatores.outrasComorbidades").value(0)).andExpect(jsonPath("$.fatores.idade").value(0))
            .andExpect(jsonPath("$.fatores.adesaoMedicacao").value(0)).andExpect(jsonPath("$.fatores.checkins").value(0))
            .andExpect(jsonPath("$.fatores.dadosAdesaoSuficientes").value(false))
            .andExpect(jsonPath("$.fatores.dadosCheckinsSuficientes").value(false));
        verify(connection).prepareStatement(contains("INSERT INTO SCORE_RISCO_RENAL"), aryEq(new String[]{"ID_SCORE"}));
        verify(insert).setInt(1, 7); verify(insert).setBigDecimal(3, new BigDecimal("100.00"));
        verify(insert).setTimestamp(eq(2), argThat(t -> t.getNanos() == 0));
        verify(insert).setString(4, "BAIXO"); verify(connection).setAutoCommit(false);
        var ordem = inOrder(insert, keys, connection);
        ordem.verify(insert).executeUpdate(); ordem.verify(keys).getLong(1); ordem.verify(keys).close();
        ordem.verify(insert).close(); ordem.verify(connection).commit();
        verify(connection, never()).rollback();
        for (var ps : new PreparedStatement[]{pessoaPs, comorbidadesPs, adesaoPs, checkinsPs}) {
            verify(ps).setInt(1, 7); verify(ps).close();
        }
    }

    @Test void consultasFiltramBeneficiarioPendenteEOrdenamSete() throws Exception {
        when(adesao.next()).thenReturn(true, true, true, false);
        when(adesao.getString("status_confirmacao")).thenReturn("CONFIRMADO", "PERDIDO", "PENDENTE");
        when(adesao.getLong("quantidade")).thenReturn(9L, 1L);
        when(checkins.next()).thenReturn(true, false); when(checkins.wasNull()).thenReturn(true);
        mvc.perform(post(URL + "/recalcular")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.fatores.adesaoMedicacao").value(0))
            .andExpect(jsonPath("$.fatores.dadosAdesaoSuficientes").value(true))
            .andExpect(jsonPath("$.fatores.dadosCheckinsSuficientes").value(false));
        verify(connection).prepareStatement(argThat(sql -> sql.contains("m.id_beneficiario = ?")
            && sql.contains("m.id_medicacao = cm.id_medicacao")
            && sql.contains("IN ('CONFIRMADO', 'PERDIDO')")));
        verify(connection).prepareStatement(contains("ORDER BY data_checkin DESC, id_checkin DESC FETCH FIRST 7 ROWS ONLY"));
        verify(connection).prepareStatement(contains("bc.id_beneficiario = ?"));
        verify(checkins, never()).getString("resposta_texto");
    }

    @Test void getRetornaPersistidoMaisRecenteSemRecalcularOuInventarFatores() throws Exception {
        when(atual.next()).thenReturn(true);
        when(atual.getLong("id_score")).thenReturn(55L); when(atual.getInt("id_beneficiario")).thenReturn(7);
        when(atual.getTimestamp("data_calculo")).thenReturn(Timestamp.valueOf("2026-09-16 12:30:45"));
        when(atual.getBigDecimal("valor_score")).thenReturn(new BigDecimal("82.00"));
        when(atual.getString("classificacao_risco")).thenReturn("BAIXO");
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().json("""
            {"idScore":55,"idBeneficiario":7,"dataCalculo":"2026-09-16T12:30:45","valorScore":82.00,"classificacaoRisco":"BAIXO"}
            """, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(connection).prepareStatement(contains("ORDER BY data_calculo DESC, id_score DESC FETCH FIRST 1 ROW ONLY"));
        verify(atualPs).setInt(1, 7); verify(atualPs).close(); verify(atual).close();
        verifyNoInteractions(insert, comorbidadesPs, adesaoPs, checkinsPs);
    }

    @Test void postExemploExatoComDadosEstruturados() throws Exception {
        when(pessoa.getDate("data_nascimento")).thenReturn(Date.valueOf("1980-01-02"));
        when(comorbidades.next()).thenReturn(true, true, false);
        when(comorbidades.getString("descricao")).thenReturn(" Hipertensão ", "Outra comorbidade");
        when(adesao.next()).thenReturn(true, false);
        when(adesao.getString("status_confirmacao")).thenReturn("CONFIRMADO");
        when(adesao.getLong("quantidade")).thenReturn(1L);
        when(checkins.next()).thenReturn(true, false);
        when(checkins.getInt("nivel_estresse")).thenReturn(1);
        when(checkins.getString("qualidade_sono")).thenReturn("BOA");
        when(checkins.getString("qualidade_alimentacao")).thenReturn("BOM");
        when(checkins.getString("humor")).thenReturn("CALMO");
        mvc.perform(post(URL + "/recalcular")).andExpect(status().isCreated()).andExpect(content().json("""
            {"idScore":42,"idBeneficiario":7,"dataCalculo":"2026-09-16T12:30:45","valorScore":82.00,
             "classificacaoRisco":"BAIXO","fatores":{"hipertensao":10,"diabetes":0,"outrasComorbidades":5,
             "idade":3,"adesaoMedicacao":0,"checkins":0,"dadosAdesaoSuficientes":true,"dadosCheckinsSuficientes":true}}
            """, org.springframework.test.json.JsonCompareMode.STRICT));
        verify(insert).setBigDecimal(3, new BigDecimal("82.00"));
    }

    @Test void getSemScore404() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isNotFound()); verifyNoInteractions(insert);
    }

    @Test void beneficiarioInexistente404EmAmbos() throws Exception {
        when(pessoa.next()).thenReturn(false);
        mvc.perform(get(URL)).andExpect(status().isNotFound());
        mvc.perform(post(URL + "/recalcular")).andExpect(status().isNotFound());
        verifyNoInteractions(insert, atualPs, comorbidadesPs, adesaoPs, checkinsPs);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "2147483648"})
    void idInvalido400(String id) throws Exception {
        mvc.perform(get("/api/beneficiarios/" + id + "/score")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/beneficiarios/" + id + "/score/recalcular")).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @ParameterizedTest @ValueSource(strings = {"insert", "semChave", "zero", "null", "linhas", "commit", "rollback"})
    void escritaFalhaFazRollbackE500Generico(String falha) throws Exception {
        SQLException erro = new SQLException("ORA-00942 jdbc:oracle: segredo");
        switch (falha) {
            case "insert" -> when(insert.executeUpdate()).thenThrow(erro);
            case "semChave" -> when(keys.next()).thenReturn(false);
            case "zero" -> when(keys.getLong(1)).thenReturn(0L);
            case "null" -> when(keys.wasNull()).thenReturn(true);
            case "linhas" -> when(insert.executeUpdate()).thenReturn(0);
            case "commit" -> doThrow(erro).when(connection).commit();
            case "rollback" -> { when(insert.executeUpdate()).thenThrow(erro); doThrow(new SQLException("Falha no rollback")).when(connection).rollback(); }
        }
        var resultado = mvc.perform(post(URL + "/recalcular")).andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}", org.springframework.test.json.JsonCompareMode.STRICT));
        verify(connection).rollback(); verify(insert).close();
        if (!falha.equals("commit")) verify(connection, never()).commit();
        if (falha.equals("rollback")) assertEquals(1, resultado.andReturn().getResolvedException().getSuppressed().length);
    }

    @ParameterizedTest @ValueSource(strings = {"beneficiario", "comorbidades", "adesao", "checkins", "atual"})
    void leituraFalha500Generico(String etapa) throws Exception {
        var ps = switch (etapa) { case "beneficiario" -> pessoaPs; case "comorbidades" -> comorbidadesPs;
            case "adesao" -> adesaoPs; case "checkins" -> checkinsPs; default -> atualPs; };
        when(ps.executeQuery()).thenThrow(new SQLException("ORA-erro senha"));
        mvc.perform(etapa.equals("atual") ? get(URL) : post(URL + "/recalcular"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}", org.springframework.test.json.JsonCompareMode.STRICT));
        verify(ps).close(); verifyNoInteractions(insert);
    }
}



