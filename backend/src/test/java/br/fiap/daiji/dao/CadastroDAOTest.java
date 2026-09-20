package br.fiap.daiji.dao;

import br.fiap.daiji.dto.CadastroRequest;
import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.service.CadastroException;
import java.sql.*;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CadastroDAOTest {
    ConnectionFactory factory;
    Connection c;
    PreparedStatement empresa,benef,auth;
    ResultSet existe,bkey,akey;
    CadastroDAO dao;
    @BeforeEach void setup() throws Exception {
        factory=mock(ConnectionFactory.class); c=mock(Connection.class);
        empresa=mock(PreparedStatement.class); benef=mock(PreparedStatement.class); auth=mock(PreparedStatement.class);
        existe=mock(ResultSet.class); bkey=mock(ResultSet.class); akey=mock(ResultSet.class);
        when(factory.conectar()).thenReturn(c);
        when(c.prepareStatement(startsWith("SELECT"))).thenReturn(empresa);
        when(c.prepareStatement(startsWith("INSERT INTO BENEFICIARIO"),any(String[].class))).thenReturn(benef);
        when(c.prepareStatement(startsWith("INSERT INTO AUTENTICACAO"),any(String[].class))).thenReturn(auth);
        when(empresa.executeQuery()).thenReturn(existe); when(existe.next()).thenReturn(true);
        when(benef.executeUpdate()).thenReturn(1); when(auth.executeUpdate()).thenReturn(1);
        when(benef.getGeneratedKeys()).thenReturn(bkey); when(auth.getGeneratedKeys()).thenReturn(akey);
        when(bkey.next()).thenReturn(true); when(akey.next()).thenReturn(true);
        when(bkey.getLong(1)).thenReturn(37L); when(akey.getLong(1)).thenReturn(42L);
        dao=new CadastroDAO(factory);
    }
    void criar() throws SQLException {
        dao.criar(new CadastroRequest(1,"Maria","52998224725","maria@example.com","1996-05-21","11950000000","omitida"),
                LocalDate.of(1996,5,21),"hash-teste");
    }
    @Test void mesmaConexaoChavesECommitAposDoisInserts() throws Exception {
        criar();
        var ordem=inOrder(c,empresa,benef,auth,bkey,akey);
        ordem.verify(c).setAutoCommit(false);
        ordem.verify(empresa).executeQuery();
        ordem.verify(benef).executeUpdate();
        ordem.verify(benef).getGeneratedKeys();
        ordem.verify(bkey).getLong(1);
        ordem.verify(auth).setInt(1,37);
        ordem.verify(auth).executeUpdate();
        ordem.verify(auth).getGeneratedKeys();
        ordem.verify(akey).getLong(1);
        ordem.verify(c).commit();
        ordem.verify(c).close();
        verify(factory,times(1)).conectar(); verify(c,never()).rollback();
        verify(empresa).close(); verify(benef).close(); verify(auth).close();
        verify(existe).close(); verify(bkey).close(); verify(akey).close();
    }
    @ParameterizedTest @ValueSource(strings={"empresa","beneficiario","autenticacao","commit","chaveBeneficiario","chaveAutenticacao"})
    void qualquerFalhaSqlFazRollback(String etapa) throws Exception {
        var falha=new SQLException("erro interno");
        switch(etapa) {
            case "empresa" -> when(empresa.executeQuery()).thenThrow(falha);
            case "beneficiario" -> when(benef.executeUpdate()).thenThrow(falha);
            case "autenticacao" -> when(auth.executeUpdate()).thenThrow(falha);
            case "commit" -> doThrow(falha).when(c).commit();
            case "chaveBeneficiario" -> when(bkey.next()).thenReturn(false);
            case "chaveAutenticacao" -> when(akey.next()).thenReturn(false);
        }
        assertThrows(SQLException.class,this::criar);
        verify(c).rollback(); verify(c).close();
        if(!etapa.equals("commit")) verify(c,never()).commit();
    }
    @Test void empresaAusenteNaoInsere() throws Exception {
        when(existe.next()).thenReturn(false);
        assertEquals(400,assertThrows(CadastroException.class,this::criar).status());
        verify(c).rollback(); verify(c,never()).commit(); verifyNoInteractions(benef,auth);
    }
    @ParameterizedTest @ValueSource(strings={"UK_BENEFICIARIO_CPF","UK_BENEFICIARIO_EMAIL","UK_AUTENTICACAO_EMAIL"})
    void oracleUniqueConhecidaVira409(String constraint) throws Exception {
        when(auth.executeUpdate()).thenThrow(new SQLException("ORA-00001: unique constraint (APP."+constraint+") violated","23000",1));
        assertEquals(409,assertThrows(CadastroException.class,this::criar).status());
        verify(c).rollback(); verify(c,never()).commit();
    }
    @Test void outraUniqueNaoViraConflitoDeCpfEmail() throws Exception {
        when(auth.executeUpdate()).thenThrow(new SQLException("ORA-00001 (APP.PK_AUTENTICACAO)","23000",1));
        assertThrows(SQLException.class,this::criar); verify(c).rollback();
    }
    @Test void rollbackFalhaNaoRetornaSucessoNem409() throws Exception {
        when(auth.executeUpdate()).thenThrow(new SQLException("UK_AUTENTICACAO_EMAIL","23000",1));
        doThrow(new SQLException("rollback")).when(c).rollback();
        assertThrows(SQLException.class,this::criar); verify(c,never()).commit(); verify(c).close();
    }
    @Test void falhaRuntimeTambemFazRollback() throws Exception {
        when(auth.executeUpdate()).thenThrow(new IllegalStateException("falha"));
        assertThrows(IllegalStateException.class,this::criar); verify(c).rollback(); verify(c,never()).commit();
    }
}
