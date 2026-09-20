package br.fiap.daiji.dao;

import br.fiap.daiji.dto.EncaminhamentoResponse;
import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class EncaminhamentoDAO {
    private final ConnectionFactory factory;
    public EncaminhamentoDAO(ConnectionFactory factory) { this.factory = factory; }

    private static final String CONSULTA = """
            SELECT e.id_encaminhamento, e.id_beneficiario, b.nome AS nome_beneficiario,
                   e.id_profissional, p.nome AS nome_profissional, p.especialidade,
                   e.data_encaminhamento, e.data_consulta, e.status_encaminhamento
            FROM ENCAMINHAMENTO e
            JOIN BENEFICIARIO b ON b.id_beneficiario = e.id_beneficiario
            JOIN PROFISSIONAL_SAUDE p ON p.id_profissional = e.id_profissional
            """;

    public List<EncaminhamentoResponse> listar(Integer empresa, String status) throws SQLException {
        String sql = CONSULTA + " WHERE 1 = 1"
                + (empresa == null ? "" : " AND b.id_empresa = ?")
                + (status == null ? "" : " AND e.status_encaminhamento = ?")
                + " ORDER BY e.data_encaminhamento DESC, e.id_encaminhamento DESC";
        List<EncaminhamentoResponse> dados = new ArrayList<>();
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (empresa != null) ps.setInt(i++, empresa);
            if (status != null) ps.setString(i, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) dados.add(mapear(rs));
            }
        }
        return dados;
    }

    public EncaminhamentoResponse criar(int beneficiario, int profissional, LocalDateTime consulta) throws SQLException {
        // IDENTITY, SYSDATE e status PENDENTE são fornecidos pelo schema oficial.
        String sql = "INSERT INTO ENCAMINHAMENTO (id_beneficiario, id_profissional, data_consulta) VALUES (?, ?, ?)";
        try (Connection c = factory.conectar()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"ID_ENCAMINHAMENTO"})) {
                    ps.setInt(1, beneficiario);
                    ps.setInt(2, profissional);
                    data(ps, 3, consulta);
                    if (ps.executeUpdate() != 1) throw new SQLException("Quantidade inesperada de registros criados.");
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("ID do encaminhamento não retornado.");
                        id = keys.getLong(1);
                        if (keys.wasNull() || id <= 0) throw new SQLException("ID do encaminhamento inválido.");
                    }
                }
                EncaminhamentoResponse resultado = buscar(c, id)
                        .orElseThrow(() -> new SQLException("Encaminhamento criado não retornado."));
                c.commit();
                return resultado;
            } catch (SQLException ex) {
                rollback(c, ex);
                throw ex;
            }
        }
    }

    public Optional<EncaminhamentoResponse> atualizar(long id, String status, LocalDateTime consulta) throws SQLException {
        try (Connection c = factory.conectar()) {
            c.setAutoCommit(false);
            try {
                int linhas;
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE ENCAMINHAMENTO SET status_encaminhamento = ?, data_consulta = ?
                        WHERE id_encaminhamento = ?
                        """)) {
                    ps.setString(1, status);
                    data(ps, 2, consulta);
                    ps.setLong(3, id);
                    linhas = ps.executeUpdate();
                }
                if (linhas == 0) {
                    c.rollback();
                    return Optional.empty();
                }
                if (linhas != 1) throw new SQLException("Quantidade inesperada de registros atualizados.");
                EncaminhamentoResponse resultado = buscar(c, id)
                        .orElseThrow(() -> new SQLException("Encaminhamento atualizado não retornado."));
                c.commit();
                return Optional.of(resultado);
            } catch (SQLException ex) {
                rollback(c, ex);
                throw ex;
            }
        }
    }

    private Optional<EncaminhamentoResponse> buscar(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(CONSULTA + " WHERE e.id_encaminhamento = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    private static EncaminhamentoResponse mapear(ResultSet rs) throws SQLException {
        Timestamp consulta = rs.getTimestamp("data_consulta");
        return new EncaminhamentoResponse(rs.getLong("id_encaminhamento"), rs.getInt("id_beneficiario"),
                rs.getString("nome_beneficiario"), rs.getInt("id_profissional"), rs.getString("nome_profissional"),
                rs.getString("especialidade"), rs.getTimestamp("data_encaminhamento").toLocalDateTime(),
                consulta == null ? null : consulta.toLocalDateTime(), rs.getString("status_encaminhamento"));
    }

    private static void data(PreparedStatement ps, int indice, LocalDateTime valor) throws SQLException {
        if (valor == null) ps.setNull(indice, Types.TIMESTAMP);
        else ps.setTimestamp(indice, Timestamp.valueOf(valor));
    }

    private static void rollback(Connection c, SQLException ex) {
        try { c.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
    }
}
