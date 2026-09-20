package br.fiap.daiji;

import br.fiap.daiji.dao.ConfirmacaoMedicacaoDAO;
import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.ConfirmacaoStatus;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class ConfirmacaoMedicacaoDAOTest {
    ConnectionFactory factory = mock(ConnectionFactory.class);
    Connection c = mock(Connection.class);
    PreparedStatement owner = mock(PreparedStatement.class), insert = mock(PreparedStatement.class), select = mock(PreparedStatement.class);
    ResultSet medication = mock(ResultSet.class), keys = mock(ResultSet.class), result = mock(ResultSet.class);
    ConfirmacaoMedicacaoDAO dao = new ConfirmacaoMedicacaoDAO(factory);
    @BeforeEach void prepare() throws Exception {
        when(factory.conectar()).thenReturn(c);
        when(c.prepareStatement(startsWith("SELECT id_medicacao"))).thenReturn(owner);
        when(c.prepareStatement(startsWith("INSERT"),any(String[].class))).thenReturn(insert);
        when(c.prepareStatement(startsWith("SELECT data_confirmacao"))).thenReturn(select);
        when(owner.executeQuery()).thenReturn(medication); when(medication.next()).thenReturn(true);
        when(insert.executeUpdate()).thenReturn(1); when(insert.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true); when(keys.getLong(1)).thenReturn(42L);
        when(select.executeQuery()).thenReturn(result); when(result.next()).thenReturn(true);
        when(result.getTimestamp("data_confirmacao")).thenReturn(Timestamp.valueOf("2026-09-17 10:30:45"));
        when(result.getString("status_confirmacao")).thenReturn("CONFIRMADO");
    }
    @Test void propriedadeTransacaoIdentityDefaultsRecursos() throws Exception {
        var r = dao.criar(1,2,ConfirmacaoStatus.CONFIRMADO).orElseThrow();
        assertEquals(42L,r.idConfirmacao()); assertEquals(1,r.idBeneficiario()); assertEquals(2,r.idMedicacao());
        assertEquals("2026-09-17T10:30:45",r.dataConfirmacao().toString());
        verify(c).prepareStatement("SELECT id_medicacao FROM MEDICACAO WHERE id_medicacao = ? AND id_beneficiario = ? FOR UPDATE");
        verify(owner).setInt(1,2); verify(owner).setInt(2,1);
        verify(c).prepareStatement(eq("INSERT INTO CONFIRMACAO_MEDICACAO (id_medicacao, status_confirmacao) VALUES (?, ?)"),aryEq(new String[]{"ID_CONFIRMACAO"}));
        verify(insert).setInt(1,2); verify(insert).setString(2,"CONFIRMADO"); verify(select).setLong(1,42L);
        var order = inOrder(c,owner,medication,insert,keys,select,result);
        order.verify(c).setAutoCommit(false); order.verify(owner).executeQuery(); order.verify(medication).close(); order.verify(owner).close();
        order.verify(insert).executeUpdate(); order.verify(keys).getLong(1); order.verify(keys).close(); order.verify(insert).close();
        order.verify(select).executeQuery(); order.verify(result).close(); order.verify(select).close(); order.verify(c).commit(); order.verify(c).close();
        verify(c,never()).rollback();
    }
    @Test void medNaoPertenceNaoInsereERollbackLiberaLock() throws Exception {
        when(medication.next()).thenReturn(false); assertTrue(dao.criar(1,2,ConfirmacaoStatus.CONFIRMADO).isEmpty());
        verify(c).rollback(); verify(c).close(); verifyNoInteractions(insert,select); verify(owner).close(); verify(medication).close();
    }
    @ParameterizedTest @ValueSource(strings = {"lock","insert","zeroLinhas","duasLinhas","semChave","chaveZero","chaveNegativa","chaveNull","select","semRegistro","semData","status","commit","rollback"})
    void falhasRollbackSemCommit(String failure) throws Exception {
        SQLException error = new SQLException("falha JDBC");
        switch (failure) {
            case "lock" -> when(owner.executeQuery()).thenThrow(error);
            case "insert","rollback" -> when(insert.executeUpdate()).thenThrow(error);
            case "zeroLinhas" -> when(insert.executeUpdate()).thenReturn(0);
            case "duasLinhas" -> when(insert.executeUpdate()).thenReturn(2);
            case "semChave" -> when(keys.next()).thenReturn(false);
            case "chaveZero" -> when(keys.getLong(1)).thenReturn(0L);
            case "chaveNegativa" -> when(keys.getLong(1)).thenReturn(-1L);
            case "chaveNull" -> when(keys.wasNull()).thenReturn(true);
            case "select" -> when(select.executeQuery()).thenThrow(error);
            case "semRegistro" -> when(result.next()).thenReturn(false);
            case "semData" -> when(result.getTimestamp("data_confirmacao")).thenReturn(null);
            case "status" -> when(result.getString("status_confirmacao")).thenReturn("INVENTADO");
            case "commit" -> doThrow(error).when(c).commit();
        }
        if (failure.equals("rollback")) doThrow(new SQLException("rollback")).when(c).rollback();
        var thrown = assertThrows(SQLException.class,() -> dao.criar(1,2,ConfirmacaoStatus.CONFIRMADO));
        verify(c).rollback(); verify(c).close(); verify(owner).close();
        if (!failure.equals("commit")) verify(c,never()).commit();
        if (!failure.equals("lock")) verify(insert).close();
        if (failure.equals("rollback")) assertEquals(1,thrown.getSuppressed().length);
    }
}
