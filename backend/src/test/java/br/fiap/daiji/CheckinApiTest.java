package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Toda a aplicação é real; somente a fronteira JDBC é simulada.
@SpringBootTest
@AutoConfigureMockMvc
class CheckinApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionFactory factory;
    Connection consulta, escrita;
    PreparedStatement beneficiario, insert, data;
    ResultSet pessoa, keys, registro;

    @BeforeEach
    void prepararJdbc() throws Exception {
        consulta = mock(Connection.class);
        escrita = mock(Connection.class);
        beneficiario = mock(PreparedStatement.class);
        insert = mock(PreparedStatement.class);
        data = mock(PreparedStatement.class);
        pessoa = mock(ResultSet.class);
        keys = mock(ResultSet.class);
        registro = mock(ResultSet.class);
        when(factory.conectar()).thenReturn(consulta, escrita);
        when(consulta.prepareStatement(anyString())).thenReturn(beneficiario);
        when(beneficiario.executeQuery()).thenReturn(pessoa);
        when(pessoa.next()).thenReturn(true);
        when(pessoa.getInt("id_beneficiario")).thenReturn(7);
        when(pessoa.getDate("data_nascimento")).thenReturn(Date.valueOf("2000-01-02"));
        when(pessoa.getDate("data_cadastro")).thenReturn(Date.valueOf("2026-09-15"));
        when(escrita.prepareStatement(anyString(), any(String[].class))).thenReturn(insert);
        when(insert.executeUpdate()).thenReturn(1);
        when(insert.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getInt(1)).thenReturn(42);
        when(escrita.prepareStatement(anyString())).thenReturn(data);
        when(data.executeQuery()).thenReturn(registro);
        when(registro.next()).thenReturn(true);
        when(registro.getTimestamp("data_checkin")).thenReturn(Timestamp.valueOf("2026-09-16 10:30:00"));
    }

    @Test
    void criaComIdDaUrlIgnorandoIdentificadoresECanalDoJson() throws Exception {
        mvc.perform(post("/api/beneficiarios/7/checkins").contentType("application/json").content("""
                {"idBeneficiario":999,"idCheckin":999,"canal":"WEB","dataCheckin":"2000-01-01",
                 "nivelEstresse":3,"qualidadeSono":"BOA","qualidadeAlimentacao":"BOA",
                 "humor":"CALMO","respostaTexto":"Dia tranquilo"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCheckin").value(42))
                .andExpect(jsonPath("$.idBeneficiario").value(7))
                .andExpect(jsonPath("$.dataCheckin").value("2026-09-16T10:30:00"))
                .andExpect(jsonPath("$.canal").value("APP"))
                .andExpect(jsonPath("$.nivelEstresse").value(3))
                .andExpect(jsonPath("$.qualidadeSono").value("BOA"))
                .andExpect(jsonPath("$.qualidadeAlimentacao").value("BOA"))
                .andExpect(jsonPath("$.humor").value("CALMO"))
                .andExpect(jsonPath("$.respostaTexto").value("Dia tranquilo"));
        verify(beneficiario).setInt(1, 7);
        verify(escrita).prepareStatement(contains("INSERT INTO CHECKIN"), aryEq(new String[]{"ID_CHECKIN"}));
        verify(insert).setInt(1, 7);
        verify(insert).setString(2, "APP");
        verify(insert).setInt(3, 3);
        verify(insert).setString(4, "BOA");
        verify(insert).setString(5, "BOA");
        verify(insert).setString(6, "CALMO");
        verify(insert).setString(7, "Dia tranquilo");
        verify(data).setInt(1, 42);
        verify(escrita).setAutoCommit(false);
        verify(escrita).commit();
        verify(escrita, never()).rollback();
        verify(pessoa).close();
        verify(beneficiario).close();
        verify(consulta).close();
        verify(keys).close();
        verify(insert).close();
        verify(registro).close();
        verify(data).close();
        verify(escrita).close();
    }

    @Test
    void aceitaCamposOpcionaisEConverteTextoVazioEmNull() throws Exception {
        mvc.perform(post("/api/beneficiarios/7/checkins").contentType("application/json")
                        .content("{\"respostaTexto\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nivelEstresse").isEmpty())
                .andExpect(jsonPath("$.respostaTexto").isEmpty());
        verify(insert).setNull(3, Types.NUMERIC);
        verify(insert).setString(7, null);
    }

    @Test
    void beneficiarioInexistenteRetorna404SemInsert() throws Exception {
        when(pessoa.next()).thenReturn(false);
        mvc.perform(post("/api/beneficiarios/7/checkins").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(escrita);
        verify(factory, times(1)).conectar();
    }

    static Stream<String> dadosInvalidos() {
        return Stream.of("{\"nivelEstresse\":0}", "{\"nivelEstresse\":6}", "{\"nivelEstresse\":\"abc\"}",
                "{\"nivelEstresse\":1.5}", "{\"nivelEstresse\":true}",
                "{\"qualidadeSono\":\"" + "a".repeat(21) + "\"}",
                "{\"qualidadeAlimentacao\":\"" + "a".repeat(31) + "\"}",
                "{\"humor\":\"" + "a".repeat(21) + "\"}",
                "{\"respostaTexto\":\"" + "a".repeat(501) + "\"}",
                "{\"humor\":\"" + "ã".repeat(11) + "\"}", "{", "", "null");
    }

    @ParameterizedTest
    @MethodSource("dadosInvalidos")
    void dadosInvalidosRetornam400SemJdbc(String body) throws Exception {
        mvc.perform(post("/api/beneficiarios/7/checkins").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    static Stream<String> idsInvalidos() {
        return Stream.of("0", "-1", "1000000000", "abc");
    }

    @ParameterizedTest
    @MethodSource("idsInvalidos")
    void idInvalidoRetorna400(String id) throws Exception {
        mvc.perform(post("/api/beneficiarios/" + id + "/checkins").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @Test
    void falhaSqlRetorna500GenericoEFazRollback() throws Exception {
        when(insert.executeUpdate()).thenThrow(new SQLException("ORA-detalhe-interno"));
        esperar500();
        verify(escrita).rollback();
        verify(escrita, never()).commit();
        verify(insert).close();
        verify(escrita).close();
    }

    @Test
    void falhaNaConsultaBeneficiarioRetorna500() throws Exception {
        when(beneficiario.executeQuery()).thenThrow(new SQLException("ORA-detalhe-interno"));
        esperar500();
        verifyNoInteractions(escrita);
    }

    @Test
    void chaveAusenteFazRollback() throws Exception {
        when(keys.next()).thenReturn(false);
        esperar500();
        verify(escrita).rollback();
        verify(escrita, never()).commit();
        verify(keys).close();
    }

    @Test
    void falhaAoRecuperarDataFazRollback() throws Exception {
        when(data.executeQuery()).thenThrow(new SQLException("ORA-detalhe-interno"));
        esperar500();
        verify(escrita).rollback();
        verify(escrita, never()).commit();
    }

    private void esperar500() throws Exception {
        mvc.perform(post("/api/beneficiarios/7/checkins").contentType("application/json").content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value("Não foi possível consultar o banco de dados."))
                .andExpect(content().string(not(containsString("ORA-detalhe-interno"))));
    }
}
