package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

// Controller, Service e DAO reais; apenas a fronteira JDBC é simulada.
@SpringBootTest
@AutoConfigureMockMvc
class BeneficiarioApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionFactory factory;
    Connection connection;
    PreparedStatement statement;
    ResultSet result;

    @BeforeEach
    void prepararJdbc() throws Exception {
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        result = mock(ResultSet.class);
        when(factory.conectar()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
    }

    @Test
    void retornaBeneficiarioJsonEFechaRecursos() throws Exception {
        when(result.next()).thenReturn(true);
        when(result.getInt("id_beneficiario")).thenReturn(7);
        when(result.getInt("id_empresa")).thenReturn(2);
        when(result.getString("nome")).thenReturn("Pessoa de Teste");
        when(result.getString("cpf")).thenReturn("00000000000");
        when(result.getString("email")).thenReturn(null);
        when(result.getString("telefone_whatsapp")).thenReturn("11900000000");
        when(result.getString("status_ativo")).thenReturn("S");
        when(result.getDate("data_nascimento")).thenReturn(Date.valueOf("2000-01-02"));
        when(result.getDate("data_cadastro")).thenReturn(Date.valueOf("2026-09-15"));

        mvc.perform(get("/api/beneficiarios/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.idEmpresa").value(2))
                .andExpect(jsonPath("$.nome").value("Pessoa de Teste"))
                .andExpect(jsonPath("$.cpf").value("00000000000"))
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.telefone").value("11900000000"))
                .andExpect(jsonPath("$.status").value("S"))
                .andExpect(jsonPath("$.dataNascimento").value("2000-01-02"))
                .andExpect(jsonPath("$.dataCadastro").value("2026-09-15"));
        verify(connection).prepareStatement(contains("WHERE id_beneficiario = ?"));
        verify(statement).setInt(1, 7);
        verify(result).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void retorna404QuandoNaoExiste() throws Exception {
        when(result.next()).thenReturn(false);
        mvc.perform(get("/api/beneficiarios/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        verify(statement).setInt(1, 99);
        verify(result).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void falhaSqlRetorna500SemVazarDetalhes() throws Exception {
        when(statement.executeQuery()).thenThrow(new SQLException("detalhe-interno"));
        mvc.perform(get("/api/beneficiarios/7"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value("Não foi possível consultar o banco de dados."))
                .andExpect(content().string(not(containsString("detalhe-interno"))));
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void falhaDeConexaoNaoRetorna404() throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("detalhe-interno"));
        mvc.perform(get("/api/beneficiarios/7")).andExpect(status().isInternalServerError());
    }

    @Test
    void idNaoNumericoRetorna400SemAcessarOracle() throws Exception {
        mvc.perform(get("/api/beneficiarios/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @Test
    void naoExpoeCadastro() throws Exception {
        mvc.perform(post("/api/beneficiarios/7")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(factory);
    }
}
