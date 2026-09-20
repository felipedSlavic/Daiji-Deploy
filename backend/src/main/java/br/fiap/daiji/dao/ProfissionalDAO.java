package br.fiap.daiji.dao;

import br.fiap.daiji.dto.ProfissionalResponse;
import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ProfissionalDAO {
    private final ConnectionFactory factory;
    public ProfissionalDAO(ConnectionFactory factory) { this.factory = factory; }

    public boolean existe(int id) throws SQLException {
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(
                "SELECT id_profissional FROM PROFISSIONAL_SAUDE WHERE id_profissional = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public List<ProfissionalResponse> listar() throws SQLException {
        List<ProfissionalResponse> dados = new ArrayList<>();
        try (Connection c = factory.conectar(); PreparedStatement ps = c.prepareStatement(
                "SELECT id_profissional, nome, crm, especialidade FROM PROFISSIONAL_SAUDE ORDER BY nome, id_profissional");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) dados.add(new ProfissionalResponse(rs.getInt("id_profissional"),
                    rs.getString("nome"), rs.getString("crm"), rs.getString("especialidade")));
        }
        return dados;
    }
}
