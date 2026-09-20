package br.fiap.daiji.dao;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.Autenticacao;
import java.sql.*;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AutenticacaoDAO {
    private final ConnectionFactory connectionFactory;

    public AutenticacaoDAO(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Optional<Autenticacao> buscarPorEmail(String email) throws SQLException {
        String sql = """
                SELECT id_autenticacao, id_beneficiario, email, senha_hash, tipo_usuario, ativo
                FROM AUTENTICACAO
                WHERE email = ?
                """;
        try (Connection connection = connectionFactory.conectar();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int id = rs.getInt("id_beneficiario");
                Integer idBeneficiario = rs.wasNull() ? null : id;
                return Optional.of(new Autenticacao(rs.getInt("id_autenticacao"),
                        idBeneficiario, rs.getString("email"), rs.getString("senha_hash"),
                        rs.getString("tipo_usuario"), rs.getString("ativo")));
            }
        }
    }
}
