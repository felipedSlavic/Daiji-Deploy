package br.fiap.daiji.dao;

import br.fiap.daiji.dto.ConfirmacaoMedicacaoResponse;
import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.ConfirmacaoStatus;
import java.sql.*;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ConfirmacaoMedicacaoDAO {
    private final ConnectionFactory factory;
    public ConfirmacaoMedicacaoDAO(ConnectionFactory factory) { this.factory = factory; }

    public Optional<ConfirmacaoMedicacaoResponse> criar(int beneficiario, int medicacao, ConfirmacaoStatus status) throws SQLException {
        try (Connection c = factory.conectar()) {
            c.setAutoCommit(false);
            try {
                // Propriedade verificada e bloqueada na mesma transação da escrita.
                boolean pertence;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_medicacao FROM MEDICACAO WHERE id_medicacao = ? AND id_beneficiario = ? FOR UPDATE")) {
                    ps.setInt(1, medicacao); ps.setInt(2, beneficiario);
                    try (ResultSet rs = ps.executeQuery()) { pertence = rs.next(); }
                }
                if (!pertence) { c.rollback(); return Optional.empty(); }
                long id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO CONFIRMACAO_MEDICACAO (id_medicacao, status_confirmacao) VALUES (?, ?)", new String[]{"ID_CONFIRMACAO"})) {
                    ps.setInt(1, medicacao); ps.setString(2, status.name());
                    if (ps.executeUpdate() != 1) throw new SQLException("Quantidade inesperada de registros criados.");
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("ID da confirmação não retornado.");
                        id = keys.getLong(1);
                        if (keys.wasNull() || id <= 0) throw new SQLException("ID da confirmação inválido.");
                    }
                }
                ConfirmacaoMedicacaoResponse result;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT data_confirmacao, status_confirmacao FROM CONFIRMACAO_MEDICACAO WHERE id_confirmacao = ?")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Confirmação não retornada.");
                        Timestamp data = rs.getTimestamp("data_confirmacao");
                        String persisted = rs.getString("status_confirmacao");
                        if (data == null || !status.name().equals(persisted)) throw new SQLException("Confirmação inconsistente.");
                        result = new ConfirmacaoMedicacaoResponse(id, beneficiario, medicacao, data.toLocalDateTime(), persisted);
                    }
                }
                c.commit();
                return Optional.of(result);
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
}
