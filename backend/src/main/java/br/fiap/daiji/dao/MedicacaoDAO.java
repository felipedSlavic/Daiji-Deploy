package br.fiap.daiji.dao;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.model.Medicacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Repository
public class MedicacaoDAO implements GenericDAO<Medicacao,Integer> {

    private final ConnectionFactory connectionFactory;

    public MedicacaoDAO(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Medicacao criar(Medicacao entidade) throws SQLException {
        String sql = "INSERT INTO MEDICACAO (id_beneficiario, nome_medicamento, dosagem, horario_previsto) VALUES (?, ?, ?, ?)";
        try (Connection connection = connectionFactory.conectar()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_MEDICACAO"})) {
                    ps.setInt(1, entidade.getBeneficiario().getId());
                    ps.setString(2, entidade.getNome());
                    ps.setString(3, entidade.getDosagem());
                    ps.setString(4, entidade.getHorario());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Quantidade inesperada de registros criados.");
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("ID da medicação não retornado.");
                        }
                        entidade.setId(keys.getInt(1));
                        if (keys.wasNull() || entidade.getId() <= 0) {
                            throw new SQLException("ID da medicação inválido.");
                        }
                    }
                }
                connection.commit();
                return entidade;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    public List<Medicacao> listarPorBeneficiario(Integer idBeneficiario) throws SQLException {
        String sql = """
                SELECT id_medicacao, id_beneficiario, nome_medicamento, dosagem, horario_previsto
                FROM MEDICACAO WHERE id_beneficiario = ? ORDER BY id_medicacao
                """;
        List<Medicacao> lista = new ArrayList<>();
        try (Connection connection = connectionFactory.conectar();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, idBeneficiario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Medicacao medicacao = new Medicacao();
                    medicacao.setId(rs.getInt("id_medicacao"));
                    Beneficiario beneficiario = new Beneficiario();
                    beneficiario.setId(rs.getInt("id_beneficiario"));
                    medicacao.setBeneficiario(beneficiario);
                    medicacao.setNome(rs.getString("nome_medicamento"));
                    medicacao.setDosagem(rs.getString("dosagem"));
                    medicacao.setHorario(rs.getString("horario_previsto"));
                    lista.add(medicacao);
                }
            }
        }
        return lista;
    }


    @Override
    public void inserir(Medicacao entidade) {
        String sql = "insert into MEDICACAO(id_beneficiario, nome_medicamento, dosagem, horario_previsto) values (? , ?, ?, ?)";
        try(Connection connection = connectionFactory.conectar();
            PreparedStatement ps = connection.prepareStatement(sql);) {
           ps.setInt(1,entidade.getBeneficiario().getId());
           ps.setString(2,entidade.getNome());
           ps.setString(3,entidade.getDosagem());
           ps.setString(4,entidade.getHorario());
           ps.execute();

        }catch (SQLException e){
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Medicacao> listar() {
        List<Medicacao> lista = new ArrayList<>();
        String sql = "select * from MEDICACAO";
        try(Connection connection = connectionFactory.conectar();
            PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()){
            while (rs.next()){
                Medicacao medicacao = new Medicacao();
                medicacao.setId(rs.getInt("id_medicacao"));
                Beneficiario beneficiario = new Beneficiario();
                beneficiario.setId(rs.getInt("id_beneficiario"));
                medicacao.setBeneficiario(beneficiario);
                medicacao.setNome(rs.getString("nome_medicamento"));
                medicacao.setDosagem(rs.getString("dosagem"));
                medicacao.setHorario(rs.getString("horario_previsto"));
                lista.add(medicacao);
            }

        }catch (SQLException e){
            System.out.println(e.getMessage());
        }
        return lista;
    }
}
