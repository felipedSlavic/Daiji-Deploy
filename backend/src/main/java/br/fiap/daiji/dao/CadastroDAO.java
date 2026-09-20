package br.fiap.daiji.dao;

import br.fiap.daiji.dto.*;
import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.service.CadastroException;
import java.sql.*;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Repository;

@Repository
public class CadastroDAO {
    private final ConnectionFactory factory;
    public CadastroDAO(ConnectionFactory factory) { this.factory=factory; }
    public LoginResponse criar(CadastroRequest r,LocalDate data,String hash) throws SQLException {
        try(Connection c=factory.conectar()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement ps=c.prepareStatement("SELECT id_empresa FROM EMPRESA WHERE id_empresa = ? FOR UPDATE")) {
                    ps.setInt(1,r.idEmpresa());
                    try(ResultSet rs=ps.executeQuery()) {
                        if(!rs.next()) throw new CadastroException(400,"Empresa informada não encontrada.");
                    }
                }
                int beneficiario;
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO BENEFICIARIO (id_empresa,nome,cpf,email,data_nascimento,telefone_whatsapp) VALUES (?,?,?,?,?,?)",new String[]{"ID_BENEFICIARIO"})) {
                    ps.setInt(1,r.idEmpresa()); ps.setString(2,r.nome()); ps.setString(3,r.cpf()); ps.setString(4,r.email());
                    ps.setDate(5,Date.valueOf(data)); ps.setString(6,r.telefoneWhatsapp());
                    beneficiario=inserir(ps);
                }
                int autenticacao;
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO AUTENTICACAO (id_beneficiario,email,senha_hash,tipo_usuario) VALUES (?,?,?,'BENEFICIARIO')",new String[]{"ID_AUTENTICACAO"})) {
                    ps.setInt(1,beneficiario); ps.setString(2,r.email()); ps.setString(3,hash);
                    autenticacao=inserir(ps);
                }
                var response=new LoginResponse(autenticacao,beneficiario,r.nome(),r.email(),"BENEFICIARIO");
                c.commit();
                return response;
            } catch(SQLException | RuntimeException e) {
                try { c.rollback(); }
                catch(SQLException rollback) { e.addSuppressed(rollback); throw new SQLException("Rollback não concluído.",e); }
                if(e instanceof SQLException sql && duplicado(sql))
                    throw new CadastroException(409,"CPF ou e-mail já cadastrado.");
                throw e;
            }
        }
    }
    private static int inserir(PreparedStatement ps) throws SQLException {
        if(ps.executeUpdate()!=1) throw new SQLException("Inserção não concluída.");
        try(ResultSet keys=ps.getGeneratedKeys()) {
            if(!keys.next()) throw new SQLException("ID não retornado.");
            long id=keys.getLong(1);
            if(keys.wasNull() || id<=0 || id>Integer.MAX_VALUE) throw new SQLException("ID inválido.");
            return (int)id;
        }
    }
    private static boolean duplicado(SQLException e) {
        for(SQLException atual=e;atual!=null;atual=atual.getNextException()) {
            String message=String.valueOf(atual.getMessage()).toUpperCase(Locale.ROOT);
            if((atual.getErrorCode()==1 || "23505".equals(atual.getSQLState()))
                    && (message.contains("UK_BENEFICIARIO_CPF") || message.contains("UK_BENEFICIARIO_EMAIL")
                    || message.contains("UK_AUTENTICACAO_EMAIL"))) return true;
        }
        return false;
    }
}
