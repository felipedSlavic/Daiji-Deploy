package br.fiap.daiji;

import br.fiap.daiji.dao.EncaminhamentoDAO;
import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;

class EncaminhamentoDAOTest {
    ConnectionFactory factory;
    Connection c;
    PreparedStatement insert, update, select;
    ResultSet keys, row;
    EncaminhamentoDAO dao;
    final LocalDateTime data = LocalDateTime.of(2026,9,20,14,30,15);

    @BeforeEach void preparar() throws Exception {
        factory = mock(ConnectionFactory.class);
        c = mock(Connection.class);
        when(factory.conectar()).thenReturn(c);
        insert = mock(PreparedStatement.class); update = mock(PreparedStatement.class); select = mock(PreparedStatement.class);
        keys = mock(ResultSet.class); row = mock(ResultSet.class);
        when(c.prepareStatement(startsWith("INSERT"), any(String[].class))).thenReturn(insert);
        when(c.prepareStatement(startsWith("UPDATE"))).thenReturn(update);
        when(c.prepareStatement(startsWith("SELECT"))).thenReturn(select);
        when(insert.executeUpdate()).thenReturn(1); when(update.executeUpdate()).thenReturn(1);
        when(insert.getGeneratedKeys()).thenReturn(keys); when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(42L); when(select.executeQuery()).thenReturn(row);
        when(row.next()).thenReturn(true);
        when(row.getLong("id_encaminhamento")).thenReturn(42L);
        when(row.getInt("id_beneficiario")).thenReturn(7);
        when(row.getInt("id_profissional")).thenReturn(2);
        when(row.getTimestamp("data_encaminhamento")).thenReturn(Timestamp.valueOf("2026-09-16 10:00:00"));
        when(row.getString("status_encaminhamento")).thenReturn("PENDENTE");
        dao = new EncaminhamentoDAO(factory);
    }

    @Test void identityDefaultsReleituraMesmoConnectionCommitSoDepoisDaLeitura() throws Exception {
        var resposta = dao.criar(7,2,null);
        assertEquals(42, resposta.idEncaminhamento()); assertEquals("PENDENTE", resposta.statusEncaminhamento());
        assertNull(resposta.dataConsulta());
        verify(c).prepareStatement(eq("INSERT INTO ENCAMINHAMENTO (id_beneficiario, id_profissional, data_consulta) VALUES (?, ?, ?)"),
                aryEq(new String[]{"ID_ENCAMINHAMENTO"}));
        verify(insert).setInt(1,7); verify(insert).setInt(2,2); verify(insert).setNull(3,Types.TIMESTAMP);
        verify(select).setLong(1,42); verify(factory).conectar();
        var ordem = inOrder(c,insert,keys,select,row);
        ordem.verify(c).setAutoCommit(false);
        ordem.verify(insert).executeUpdate(); ordem.verify(keys).getLong(1);
        ordem.verify(keys).close(); ordem.verify(insert).close();
        ordem.verify(select).executeQuery(); ordem.verify(row).close(); ordem.verify(select).close();
        ordem.verify(c).commit(); ordem.verify(c).close();
        verify(c,never()).rollback();
    }

    @Test void criarPreservaHorarioComSetTimestamp() throws Exception {
        when(row.getTimestamp("data_consulta")).thenReturn(Timestamp.valueOf(data));
        assertEquals(data, dao.criar(7,2,data).dataConsulta());
        verify(insert).setTimestamp(3,Timestamp.valueOf(data));
    }

    @ParameterizedTest @ValueSource(strings = {"insert","semChave","zero","negativo","null","linhasZero","linhasDuas","leitura","semRegistro","commit","rollback"})
    void falhaCriacaoRollbackERecursosFechados(String falha) throws Exception {
        SQLException erro = new SQLException("erro JDBC interno");
        switch (falha) {
            case "insert" -> when(insert.executeUpdate()).thenThrow(erro);
            case "semChave" -> when(keys.next()).thenReturn(false);
            case "zero" -> when(keys.getLong(1)).thenReturn(0L);
            case "negativo" -> when(keys.getLong(1)).thenReturn(-1L);
            case "null" -> when(keys.wasNull()).thenReturn(true);
            case "linhasZero" -> when(insert.executeUpdate()).thenReturn(0);
            case "linhasDuas" -> when(insert.executeUpdate()).thenReturn(2);
            case "leitura" -> when(select.executeQuery()).thenThrow(erro);
            case "semRegistro" -> when(row.next()).thenReturn(false);
            case "commit" -> doThrow(erro).when(c).commit();
            case "rollback" -> { when(insert.executeUpdate()).thenThrow(erro); doThrow(new SQLException("rollback")).when(c).rollback(); }
        }
        var ex = assertThrows(SQLException.class, () -> dao.criar(7,2,null));
        verify(c).rollback(); verify(insert).close(); verify(c).close();
        if (!falha.equals("commit")) verify(c,never()).commit();
        if (falha.equals("rollback")) assertEquals(1,ex.getSuppressed().length);
    }

    @Test void updateVinculaParametrosECommitAposReleitura() throws Exception {
        when(row.getString("status_encaminhamento")).thenReturn("AGENDADO");
        when(row.getTimestamp("data_consulta")).thenReturn(Timestamp.valueOf(data));
        var resposta = dao.atualizar(42,"AGENDADO",data).orElseThrow();
        assertEquals("AGENDADO",resposta.statusEncaminhamento()); assertEquals(data,resposta.dataConsulta());
        verify(update).setString(1,"AGENDADO"); verify(update).setTimestamp(2,Timestamp.valueOf(data)); verify(update).setLong(3,42);
        var ordem = inOrder(c,update,select,row);
        ordem.verify(c).setAutoCommit(false); ordem.verify(update).executeUpdate(); ordem.verify(update).close();
        ordem.verify(select).executeQuery(); ordem.verify(row).close(); ordem.verify(select).close();
        ordem.verify(c).commit(); ordem.verify(c).close();
    }

    @Test void updateNaoEncontradoNaoConfirmaNemConsulta() throws Exception {
        when(update.executeUpdate()).thenReturn(0);
        assertTrue(dao.atualizar(999,"CANCELADO",null).isEmpty());
        verify(update).setNull(2,Types.TIMESTAMP);
        verify(c).rollback(); verify(c,never()).commit(); verifyNoInteractions(select); verify(c).close();
    }

    @ParameterizedTest @ValueSource(strings = {"update","linhasDuas","leitura","semRegistro","commit","rollback"})
    void falhaAtualizacaoRollback(String falha) throws Exception {
        SQLException erro = new SQLException("erro JDBC interno");
        switch (falha) {
            case "update" -> when(update.executeUpdate()).thenThrow(erro);
            case "linhasDuas" -> when(update.executeUpdate()).thenReturn(2);
            case "leitura" -> when(select.executeQuery()).thenThrow(erro);
            case "semRegistro" -> when(row.next()).thenReturn(false);
            case "commit" -> doThrow(erro).when(c).commit();
            case "rollback" -> { when(update.executeUpdate()).thenThrow(erro); doThrow(new SQLException("rollback")).when(c).rollback(); }
        }
        var ex = assertThrows(SQLException.class, () -> dao.atualizar(42,"CANCELADO",null));
        verify(c).rollback(); verify(update).close(); verify(c).close();
        if (!falha.equals("commit")) verify(c,never()).commit();
        if (falha.equals("rollback")) assertEquals(1,ex.getSuppressed().length);
    }
}
