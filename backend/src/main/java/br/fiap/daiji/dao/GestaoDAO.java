package br.fiap.daiji.dao;

import br.fiap.daiji.dto.DashboardResponse;
import br.fiap.daiji.dto.GestaoBeneficiarioResponse;
import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class GestaoDAO {
    private final ConnectionFactory factory;

    public GestaoDAO(ConnectionFactory factory) { this.factory = factory; }

    // O filtro de risco só pode ser aplicado DEPOIS de selecionar o score atual.
    private static final String POPULACAO = """
            WITH scores_ordenados AS (
                SELECT s.*, ROW_NUMBER() OVER (
                    PARTITION BY id_beneficiario ORDER BY data_calculo DESC, id_score DESC
                ) AS posicao
                FROM SCORE_RISCO_RENAL s
            ), populacao AS (
                SELECT b.id_beneficiario, b.id_empresa, b.nome, b.email, b.status_ativo,
                       s.id_score, s.valor_score, s.classificacao_risco, s.data_calculo
                FROM BENEFICIARIO b
                LEFT JOIN scores_ordenados s ON s.id_beneficiario = b.id_beneficiario AND s.posicao = 1
                %s
            )
            """;

    private static String populacao(Integer empresa) {
        return POPULACAO.formatted(empresa == null ? "" : "WHERE b.id_empresa = ?");
    }

    public boolean empresaExiste(int id) throws SQLException {
        try (Connection c = factory.conectar();
             PreparedStatement ps = c.prepareStatement("SELECT id_empresa FROM EMPRESA WHERE id_empresa = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public DashboardResponse dashboard(Integer empresa) throws SQLException {
        String sql = populacao(empresa) + """
                SELECT r.*,
                       (SELECT COUNT(*) FROM ENCAMINHAMENTO e
                        JOIN populacao p ON p.id_beneficiario = e.id_beneficiario
                        WHERE e.status_encaminhamento = 'PENDENTE') AS pendentes
                FROM (
                    SELECT COUNT(*) AS total, COUNT(id_score) AS com_score,
                           COUNT(CASE WHEN classificacao_risco = 'BAIXO' THEN 1 END) AS baixo,
                           COUNT(CASE WHEN classificacao_risco = 'MEDIO' THEN 1 END) AS medio,
                           COUNT(CASE WHEN classificacao_risco = 'ALTO' THEN 1 END) AS alto,
                           ROUND(AVG(valor_score), 2) AS media
                    FROM populacao
                ) r
                """;
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (empresa != null) ps.setInt(1, empresa);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Resumo não retornado.");
                long total = rs.getLong("total"), comScore = rs.getLong("com_score");
                return new DashboardResponse(empresa, total, comScore, total - comScore,
                        rs.getLong("baixo"), rs.getLong("medio"), rs.getLong("alto"),
                        rs.getBigDecimal("media"), rs.getLong("pendentes"));
            }
        }
    }

    public List<GestaoBeneficiarioResponse> beneficiarios(Integer empresa, boolean somenteAtencao) throws SQLException {
        String sql = populacao(empresa) + "SELECT * FROM populacao "
                + (somenteAtencao ? "WHERE classificacao_risco = 'ALTO' " : "") + """
                ORDER BY CASE classificacao_risco WHEN 'ALTO' THEN 1 WHEN 'MEDIO' THEN 2
                         WHEN 'BAIXO' THEN 3 ELSE 4 END,
                         valor_score ASC NULLS LAST, nome, id_beneficiario
                """;
        List<GestaoBeneficiarioResponse> dados = new ArrayList<>();
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (empresa != null) ps.setInt(1, empresa);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp data = rs.getTimestamp("data_calculo");
                    dados.add(new GestaoBeneficiarioResponse(rs.getInt("id_beneficiario"),
                            rs.getInt("id_empresa"), rs.getString("nome"), rs.getString("email"),
                            rs.getString("status_ativo"), rs.getBigDecimal("valor_score"),
                            rs.getString("classificacao_risco"), data == null ? null : data.toLocalDateTime()));
                }
            }
        }
        return dados;
    }
}

