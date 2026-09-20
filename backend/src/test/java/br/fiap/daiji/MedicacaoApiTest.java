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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MedicacaoApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionFactory factory;
    Connection consulta, escrita;
    PreparedStatement beneficiario, insert, select;
    ResultSet pessoa, keys, registros;
    static final String URL = "/api/beneficiarios/7/medicacoes";
    static final String BODY = "{\"nomeMedicamento\":\"Medicamento demonstrativo\"}";

    @BeforeEach
    void prepararJdbc() throws Exception {
        consulta = mock(Connection.class);
        escrita = mock(Connection.class);
        beneficiario = mock(PreparedStatement.class);
        insert = mock(PreparedStatement.class);
        select = mock(PreparedStatement.class);
        pessoa = mock(ResultSet.class);
        keys = mock(ResultSet.class);
        registros = mock(ResultSet.class);
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
        when(escrita.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(registros);
    }

    @Test
    void criaComIdGeradoEBeneficiarioDaUrl() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content("""
                {"idBeneficiario":999,"idMedicacao":999,"nomeMedicamento":"Exemplo",
                 "dosagem":"10 mg","horarioPrevisto":"08:00"}
                """))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                {"idMedicacao":42,"idBeneficiario":7,"nomeMedicamento":"Exemplo",
                 "dosagem":"10 mg","horarioPrevisto":"08:00"}
                """));
        verify(beneficiario).setInt(1, 7);
        verify(escrita).prepareStatement(contains("INSERT INTO MEDICACAO"), aryEq(new String[]{"ID_MEDICACAO"}));
        verify(insert).setInt(1, 7);
        verify(insert).setString(2, "Exemplo");
        verify(insert).setString(3, "10 mg");
        verify(insert).setString(4, "08:00");
        var ordem = inOrder(insert, keys, escrita);
        ordem.verify(insert).executeUpdate();
        ordem.verify(keys).getInt(1);
        ordem.verify(keys).close();
        ordem.verify(insert).close();
        ordem.verify(escrita).commit();
        ordem.verify(escrita).close();
        verify(escrita).setAutoCommit(false);
        verify(escrita, never()).rollback();
        verify(pessoa).close();
        verify(beneficiario).close();
        verify(consulta).close();
    }

    @Test
    void opcionaisVaziosOuAusentesSaoNull() throws Exception {
        mvc.perform(post(URL).contentType("application/json")
                .content("{\"nomeMedicamento\":\"Exemplo\",\"dosagem\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dosagem").isEmpty())
                .andExpect(jsonPath("$.horarioPrevisto").isEmpty());
        verify(insert).setString(3, null);
        verify(insert).setString(4, null);
    }

    @Test
    void aceitaLimitesSemInventarFormatoDeHorario() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content(
                "{\"nomeMedicamento\":\"" + "a".repeat(100) + "\",\"dosagem\":\"" + "b".repeat(50)
                + "\",\"horarioPrevisto\":\"livre\"}"))
                .andExpect(status().isCreated());
    }

    static Stream<String> invalidos() {
        return Stream.of("{}", "{\"nomeMedicamento\":null}", "{\"nomeMedicamento\":\"\"}",
                "{\"nomeMedicamento\":\"   \"}", "{", "", "null",
                "{\"nomeMedicamento\":\"" + "a".repeat(101) + "\"}",
                "{\"nomeMedicamento\":\"" + "ã".repeat(51) + "\"}",
                "{\"nomeMedicamento\":\"Exemplo\",\"dosagem\":\"" + "a".repeat(51) + "\"}",
                "{\"nomeMedicamento\":\"Exemplo\",\"horarioPrevisto\":\"123456\"}");
    }

    @ParameterizedTest @MethodSource("invalidos")
    void invalidoRetorna400SemJdbc(String body) throws Exception {
        mvc.perform(post(URL).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    static Stream<String> idsInvalidos() { return Stream.of("0", "-1", "abc", "2147483648"); }

    @ParameterizedTest @MethodSource("idsInvalidos")
    void idsInvalidosEmAmbosEndpoints(String id) throws Exception {
        String url = "/api/beneficiarios/" + id + "/medicacoes";
        mvc.perform(post(url).contentType("application/json").content(BODY)).andExpect(status().isBadRequest());
        mvc.perform(get(url)).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @Test
    void postBeneficiarioInexistente() throws Exception {
        when(pessoa.next()).thenReturn(false);
        mvc.perform(post(URL).contentType("application/json").content(BODY)).andExpect(status().isNotFound());
        verifyNoInteractions(escrita);
    }

    @Test
    void getBeneficiarioInexistente() throws Exception {
        when(pessoa.next()).thenReturn(false);
        mvc.perform(get(URL)).andExpect(status().isNotFound());
        verifyNoInteractions(escrita);
    }

    @Test
    void listaFiltradaOrdenadaComTodosOsCampos() throws Exception {
        when(registros.next()).thenReturn(true, true, false);
        when(registros.getInt("id_medicacao")).thenReturn(42, 43);
        when(registros.getInt("id_beneficiario")).thenReturn(7);
        when(registros.getString("nome_medicamento")).thenReturn("Exemplo", "Outro");
        when(registros.getString("dosagem")).thenReturn("10 mg", (String) null);
        when(registros.getString("horario_previsto")).thenReturn("08:00", (String) null);
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().json("""
                [{"idMedicacao":42,"idBeneficiario":7,"nomeMedicamento":"Exemplo","dosagem":"10 mg","horarioPrevisto":"08:00"},
                 {"idMedicacao":43,"idBeneficiario":7,"nomeMedicamento":"Outro","dosagem":null,"horarioPrevisto":null}]
                """));
        verify(escrita).prepareStatement("""
                SELECT id_medicacao, id_beneficiario, nome_medicamento, dosagem, horario_previsto
                FROM MEDICACAO WHERE id_beneficiario = ? ORDER BY id_medicacao
                """);
        verify(select).setInt(1, 7);
        verify(beneficiario).setInt(1, 7);
        verify(registros).close();
        verify(select).close();
        verify(escrita).close();
    }

    @Test
    void listaVazia() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void falhaGetNaoExpoeDetalhes() throws Exception {
        when(select.executeQuery()).thenThrow(new SQLException("ORA-00942 jdbc:oracle: segredo"));
        mvc.perform(get(URL)).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}", org.springframework.test.json.JsonCompareMode.STRICT));
        verify(select).close();
        verify(escrita).close();
    }

    static Stream<String> falhas() {
        return Stream.of("insert", "semChave", "chaveZero", "chaveNull", "zeroLinhas", "commit", "rollback");
    }

    @ParameterizedTest @MethodSource("falhas")
    void falhasEscritaFazemRollbackSemExporDetalhes(String falha) throws Exception {
        SQLException erro = new SQLException("ORA-00942 jdbc:oracle: usuario senha stack trace");
        switch (falha) {
            case "insert" -> when(insert.executeUpdate()).thenThrow(erro);
            case "semChave" -> when(keys.next()).thenReturn(false);
            case "chaveZero" -> when(keys.getInt(1)).thenReturn(0);
            case "chaveNull" -> when(keys.wasNull()).thenReturn(true);
            case "zeroLinhas" -> when(insert.executeUpdate()).thenReturn(0);
            case "commit" -> doThrow(erro).when(escrita).commit();
            case "rollback" -> {
                when(insert.executeUpdate()).thenThrow(erro);
                doThrow(erro).when(escrita).rollback();
            }
        }
        esperarPost500();
        verify(escrita).rollback();
        if (!falha.equals("commit")) verify(escrita, never()).commit();
        verify(insert).close();
        verify(escrita).close();
    }

    @Test
    void falhaConsultaBeneficiarioEmAmbosEndpoints() throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("ORA-erro interno"));
        esperarPost500();
        mvc.perform(get(URL)).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}", org.springframework.test.json.JsonCompareMode.STRICT));
        verifyNoInteractions(escrita);
    }

    private void esperarPost500() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}", org.springframework.test.json.JsonCompareMode.STRICT));
    }
}

