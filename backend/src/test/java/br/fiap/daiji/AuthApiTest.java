package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionFactory factory;
    Connection connection;
    PreparedStatement authStatement;
    PreparedStatement beneficiarioStatement;
    ResultSet auth;
    ResultSet beneficiario;
    static final String HASH = new BCryptPasswordEncoder().encode("Daiji@123");

    @BeforeEach
    void prepararJdbc() throws Exception {
        connection = mock(Connection.class);
        authStatement = mock(PreparedStatement.class);
        beneficiarioStatement = mock(PreparedStatement.class);
        auth = mock(ResultSet.class);
        beneficiario = mock(ResultSet.class);
        when(factory.conectar()).thenReturn(connection);
        when(connection.prepareStatement(contains("FROM AUTENTICACAO"))).thenReturn(authStatement);
        when(connection.prepareStatement(contains("FROM BENEFICIARIO"))).thenReturn(beneficiarioStatement);
        when(authStatement.executeQuery()).thenReturn(auth);
        when(beneficiarioStatement.executeQuery()).thenReturn(beneficiario);
        when(auth.next()).thenReturn(true);
        when(auth.getInt("id_autenticacao")).thenReturn(10);
        when(auth.getInt("id_beneficiario")).thenReturn(1);
        when(auth.getString("email")).thenReturn("rafael@daiji.com");
        when(auth.getString("senha_hash")).thenReturn(HASH);
        when(auth.getString("tipo_usuario")).thenReturn("BENEFICIARIO");
        when(auth.getString("ativo")).thenReturn("S");
        when(beneficiario.next()).thenReturn(true);
        when(beneficiario.getString("nome")).thenReturn("Rafael Almeida");
        when(beneficiario.getDate("data_nascimento")).thenReturn(Date.valueOf("2000-01-01"));
        when(beneficiario.getDate("data_cadastro")).thenReturn(Date.valueOf("2026-09-15"));
    }

    ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    void rejeitar() throws Exception {
        login("rafael@daiji.com", "Daiji@123").andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"mensagem\":\"Credenciais inválidas.\"}"));
        verifyNoInteractions(beneficiarioStatement);
    }

    @Test
    void beneficiarioValido() throws Exception {
        login("rafael@daiji.com", "Daiji@123").andExpect(status().isOk())
                .andExpect(content().json("""
                        {"idAutenticacao":10,"idBeneficiario":1,"nome":"Rafael Almeida",
                         "email":"rafael@daiji.com","tipoUsuario":"BENEFICIARIO"}
                        """))
                .andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
        verify(authStatement).setString(1, "rafael@daiji.com");
        verify(connection).prepareStatement(contains("WHERE email = ?"));
        verify(beneficiarioStatement).setInt(1, 1);
        verify(auth).close();
        verify(authStatement).close();
        verify(beneficiario).close();
        verify(beneficiarioStatement).close();
        verify(connection, times(2)).close();
    }

    @Test
    void senhaIncorreta() throws Exception {
        login("rafael@daiji.com", "errada").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("Credenciais inválidas."));
        verifyNoInteractions(beneficiarioStatement);
    }

    @Test
    void emailInexistente() throws Exception {
        when(auth.next()).thenReturn(false);
        rejeitar();
    }

    @Test
    void inativo() throws Exception {
        when(auth.getString("ativo")).thenReturn("N");
        rejeitar();
    }

    @Test
    void gestorComVinculoNulo() throws Exception {
        when(auth.getInt("id_beneficiario")).thenReturn(0);
        when(auth.wasNull()).thenReturn(true);
        when(auth.getString("tipo_usuario")).thenReturn("GESTOR");
        when(auth.getString("email")).thenReturn("gestor@daiji.com");
        login("gestor@daiji.com", "Daiji@123").andExpect(status().isOk())
                .andExpect(content().json("""
                        {"idAutenticacao":10,"idBeneficiario":null,"nome":null,
                         "email":"gestor@daiji.com","tipoUsuario":"GESTOR"}
                        """));
        verify(auth).wasNull();
        verifyNoInteractions(beneficiarioStatement);
    }

    @Test
    void tipoInvalido() throws Exception {
        when(auth.getString("tipo_usuario")).thenReturn("ADMIN");
        rejeitar();
    }

    @Test
    void beneficiarioSemVinculo() throws Exception {
        when(auth.wasNull()).thenReturn(true);
        rejeitar();
    }

    @Test
    void hashEmTextoPuroNaoAutentica() throws Exception {
        when(auth.getString("senha_hash")).thenReturn("Daiji@123");
        rejeitar();
    }

    @Test
    void camposAusentes() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(factory);
    }

    @Test
    void erroBancoGenerico() throws Exception {
        when(authStatement.executeQuery()).thenThrow(new SQLException("detalhe-interno"));
        login("rafael@daiji.com", "Daiji@123").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value("Não foi possível consultar o banco de dados."))
                .andExpect(content().string(not(containsString("detalhe-interno"))));
        verify(authStatement).close();
        verify(connection).close();
    }
}
