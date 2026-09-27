package br.fiap.daiji.dao;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.service.BeneficiarioService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BeneficiarioEmailTest {
    static final String DB = "jdbc:h2:mem:beneficiarioEmail;MODE=Oracle;DB_CLOSE_DELAY=-1";
    ConnectionFactory factory = mock(ConnectionFactory.class);
    BeneficiarioDAO dao = new BeneficiarioDAO(factory);
    BeneficiarioService service = new BeneficiarioService(dao);

    @BeforeEach void prepare() throws Exception {
        when(factory.conectar()).thenAnswer(call -> DriverManager.getConnection(DB));
        try (var connection = DriverManager.getConnection(DB);
             var statement = connection.createStatement();
             var reader = new InputStreamReader(getClass().getResourceAsStream("/cp06-fixture.sql"), StandardCharsets.UTF_8)) {
            statement.execute("DROP ALL OBJECTS");
            RunScript.execute(connection, reader);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"rafael@daiji.com", "RAFAEL@DAIJI.COM", "  Rafael@Daiji.Com  "})
    void consultaRealIgnoraCaixaEEspacosEMapeiaBeneficiario(String email) throws Exception {
        var person = service.buscarPorEmail(email).orElseThrow();
        assertEquals(1, person.getId());
        assertEquals(1, person.getEmpresa().getId());
        assertEquals("Rafael Almeida", person.getNome());
        assertEquals("rafael@daiji.com", person.getEmail());
        assertEquals("11111111111", person.getCpf());
        assertEquals(LocalDate.of(1980, 1, 2), person.getDataNascimento());
        assertNotNull(person.getDataCadastro());
        assertEquals("11900000001", person.getTelefone());
        assertEquals("S", person.getStatus());
        assertEquals(2, service.buscarPorEmail("maria@daiji.com").orElseThrow().getId());
    }
    @Test void inexistenteRetornaOptionalVazio() throws Exception {
        assertTrue(service.buscarPorEmail("ausente@daiji.com").isEmpty());
    }
    @Test void parametroNaoPodeModificarConsultaSql() throws Exception {
        assertTrue(dao.buscarPorEmail("' OR '1'='1").isEmpty());
        assertEquals(1, dao.buscarPorId(1).orElseThrow().getId());
    }
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {" ", "123", "sem-arroba.com", "a@", "@b.com", "a@b", "a b@c.com", "a@@b.com", "a@b..com"})
    void formatoInvalidoNaoAbreConexao(String email) {
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorEmail(email));
        verifyNoInteractions(factory);
    }
    @Test void falhaSqlPropagadaAoCanalSemVirarAusencia() throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("falha de conexão"));
        assertThrows(SQLException.class, () -> service.buscarPorEmail("rafael@daiji.com"));
    }
}
