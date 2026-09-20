package br.fiap.daiji.dao;

import br.fiap.daiji.dto.ScoreResponse;
import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.ScoreDados;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ScoreDAO {
    private final ConnectionFactory factory;

    public ScoreDAO(ConnectionFactory factory) { this.factory = factory; }

    public List<String> comorbidades(int id) throws SQLException {
        String sql = """
                SELECT c.descricao FROM COMORBIDADE c
                JOIN BENEFICIARIO_COMORBIDADE bc ON bc.id_comorbidade = c.id_comorbidade
                WHERE bc.id_beneficiario = ?
                """;
        List<String> dados = new ArrayList<>();
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) dados.add(rs.getString("descricao"));
            }
        }
        return dados;
    }

    public ScoreDados.Adesao adesao(int id) throws SQLException {
        String sql = """
                SELECT cm.status_confirmacao, COUNT(*) AS quantidade
                FROM CONFIRMACAO_MEDICACAO cm
                JOIN MEDICACAO m ON m.id_medicacao = cm.id_medicacao
                WHERE m.id_beneficiario = ? AND cm.status_confirmacao IN ('CONFIRMADO', 'PERDIDO')
                GROUP BY cm.status_confirmacao
                """;
        long confirmados = 0, perdidos = 0;
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    switch (rs.getString("status_confirmacao")) {
                        case "CONFIRMADO" -> confirmados = rs.getLong("quantidade");
                        case "PERDIDO" -> perdidos = rs.getLong("quantidade");
                        default -> { }
                    }
                }
            }
        }
        return new ScoreDados.Adesao(confirmados, perdidos);
    }

    public List<ScoreDados.Checkin> ultimosCheckins(int id) throws SQLException {
        String sql = """
                SELECT nivel_estresse, qualidade_sono, qualidade_alimentacao, humor
                FROM CHECKIN WHERE id_beneficiario = ?
                ORDER BY data_checkin DESC, id_checkin DESC FETCH FIRST 7 ROWS ONLY
                """;
        List<ScoreDados.Checkin> dados = new ArrayList<>();
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int estresse = rs.getInt("nivel_estresse");
                    Integer valor = rs.wasNull() ? null : estresse;
                    dados.add(new ScoreDados.Checkin(valor, rs.getString("qualidade_sono"),
                            rs.getString("qualidade_alimentacao"), rs.getString("humor")));
                }
            }
        }
        return dados;
    }

    public ScoreResponse criar(ScoreResponse score) throws SQLException {
        String sql = """
                INSERT INTO SCORE_RISCO_RENAL
                (id_beneficiario, data_calculo, valor_score, classificacao_risco) VALUES (?, ?, ?, ?)
                """;
        try (Connection c = factory.conectar()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"ID_SCORE"})) {
                    ps.setInt(1, score.idBeneficiario());
                    ps.setTimestamp(2, Timestamp.valueOf(score.dataCalculo()));
                    ps.setBigDecimal(3, score.valorScore());
                    ps.setString(4, score.classificacaoRisco());
                    if (ps.executeUpdate() != 1) throw new SQLException("Quantidade inesperada de registros criados.");
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("ID do score não retornado.");
                        id = keys.getLong(1);
                        if (keys.wasNull() || id <= 0) throw new SQLException("ID do score inválido.");
                    }
                }
                c.commit();
                return new ScoreResponse(id, score.idBeneficiario(), score.dataCalculo(),
                        score.valorScore(), score.classificacaoRisco(), score.fatores());
            } catch (SQLException ex) {
                try { c.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        }
    }

    public Optional<ScoreResponse> atual(int id) throws SQLException {
        String sql = """
                SELECT id_score, id_beneficiario, data_calculo, valor_score, classificacao_risco
                FROM SCORE_RISCO_RENAL WHERE id_beneficiario = ?
                ORDER BY data_calculo DESC, id_score DESC FETCH FIRST 1 ROW ONLY
                """;
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new ScoreResponse(rs.getLong("id_score"), rs.getInt("id_beneficiario"),
                        rs.getTimestamp("data_calculo").toLocalDateTime(), rs.getBigDecimal("valor_score"),
                        rs.getString("classificacao_risco"), null));
            }
        }
    }
}
