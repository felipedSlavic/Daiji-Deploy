package br.fiap.daiji;

import br.fiap.daiji.dao.*;
import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.service.CheckinService;
import java.sql.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckinRecentesTest {
    ConnectionFactory factory = mock(ConnectionFactory.class);
    BeneficiarioDAO pessoas = mock(BeneficiarioDAO.class);
    CheckinDAO dao = new CheckinDAO(factory);
    CheckinService service = new CheckinService(pessoas,dao);
    String url = "jdbc:h2:mem:cp07;MODE=Oracle;DB_CLOSE_DELAY=-1";
    @BeforeEach void prepare() throws Exception {
        when(factory.conectar()).thenAnswer(call -> DriverManager.getConnection(url));
        when(pessoas.buscarPorId(1)).thenReturn(Optional.of(new Beneficiario()));
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            s.execute("CREATE TABLE CHECKIN (id_checkin NUMBER PRIMARY KEY, id_beneficiario NUMBER, data_checkin TIMESTAMP, nivel_estresse NUMBER(1), qualidade_sono VARCHAR2(20), qualidade_alimentacao VARCHAR2(30), humor VARCHAR2(20))");
        }
    }
    @Test void ordenacaoLimiteFiltroNullETimestamp() throws Exception {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("INSERT INTO CHECKIN VALUES (1,1,TIMESTAMP '2026-09-15 10:30:00',3,'BOA','BOA','ANTIGO')");
            s.execute("INSERT INTO CHECKIN VALUES (2,1,TIMESTAMP '2026-09-16 10:30:00',4,'REGULAR','BOA','SEGUNDO')");
            s.execute("INSERT INTO CHECKIN VALUES (3,1,TIMESTAMP '2026-09-16 10:30:00',5,'RUIM','BOA','PRIMEIRO')");
            s.execute("INSERT INTO CHECKIN VALUES (4,1,TIMESTAMP '2026-09-17 10:30:01',NULL,NULL,NULL,NULL)");
            s.execute("INSERT INTO CHECKIN VALUES (5,2,TIMESTAMP '2026-09-18 10:30:00',1,'BOA','BOA','OUTRA PESSOA')");
        }
        var result = service.recentes(1);
        assertEquals(3,result.size()); assertNull(result.getFirst().estresse()); assertNull(result.getFirst().sono());
        assertNull(result.getFirst().alimentacao()); assertNull(result.getFirst().humor());
        assertEquals(1,result.getFirst().data().getSecond());
        assertEquals("PRIMEIRO",result.get(1).humor()); assertEquals("SEGUNDO",result.get(2).humor());
    }
    @Test void listaVazia() throws Exception { assertTrue(service.recentes(1).isEmpty()); }
    @Test void inexistenteNaoConsultaCheckin() throws Exception {
        assertEquals(404,assertThrows(ResponseStatusException.class,() -> service.recentes(2)).getStatusCode().value());
        verifyNoInteractions(factory);
    }
    @ParameterizedTest @NullSource @ValueSource(ints = {0,-1})
    void invalido(Integer id) {
        assertEquals(400,assertThrows(ResponseStatusException.class,() -> service.recentes(id)).getStatusCode().value());
        verifyNoInteractions(factory,pessoas);
    }
    @Test void erroPropagaEFechaRecursos() throws Exception {
        Connection c = mock(Connection.class); PreparedStatement ps = mock(PreparedStatement.class);
        when(factory.conectar()).thenReturn(c); when(c.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenThrow(new SQLException("falha"));
        assertThrows(SQLException.class,() -> service.recentes(1));
        verify(ps).setInt(1,1); verify(ps).close(); verify(c).close();
    }
    @Test void sucessoFechaTodosRecursos() throws Exception {
        Connection c = mock(Connection.class); PreparedStatement ps = mock(PreparedStatement.class); ResultSet rs = mock(ResultSet.class);
        when(factory.conectar()).thenReturn(c); when(c.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs); service.recentes(1);
        verify(rs).close(); verify(ps).close(); verify(c).close();
    }
}
