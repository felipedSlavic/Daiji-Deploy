package br.fiap.daiji.dao;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.dto.CheckinResumo;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.model.Checkin;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Repository
public class CheckinDAO implements GenericDAO<Checkin, Integer> {

    private final ConnectionFactory connectionFactory;

    public CheckinDAO(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public List<CheckinResumo> recentes(Integer idBeneficiario) throws SQLException {
        String sql = """
                SELECT data_checkin, nivel_estresse, qualidade_sono, qualidade_alimentacao, humor
                FROM CHECKIN WHERE id_beneficiario = ?
                ORDER BY data_checkin DESC, id_checkin DESC FETCH FIRST 3 ROWS ONLY
                """;
        List<CheckinResumo> resultado = new ArrayList<>();
        try (Connection c = connectionFactory.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBeneficiario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp data = rs.getTimestamp("data_checkin");
                    int estresse = rs.getInt("nivel_estresse");
                    Integer nivel = rs.wasNull() ? null : estresse;
                    resultado.add(new CheckinResumo(data == null ? null : data.toLocalDateTime(),
                            nivel, rs.getString("qualidade_sono"), rs.getString("qualidade_alimentacao"), rs.getString("humor")));
                }
            }
        }
        return List.copyOf(resultado);
    }

    public Checkin criar(Checkin entidade) throws SQLException {
        String sql = """
                INSERT INTO CHECKIN (id_beneficiario, canal, nivel_estresse,
                    qualidade_sono, qualidade_alimentacao, humor, resposta_texto)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionFactory.conectar()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID_CHECKIN"})) {
                    ps.setInt(1, entidade.getBeneficiario().getId());
                    ps.setString(2, entidade.getCanal());
                    if (entidade.getNivelEstresse() == null) {
                        ps.setNull(3, Types.NUMERIC);
                    } else {
                        ps.setInt(3, entidade.getNivelEstresse());
                    }
                    ps.setString(4, entidade.getQualidadeSono());
                    ps.setString(5, entidade.getQualidadeAlimentacao());
                    ps.setString(6, entidade.getHumor());
                    ps.setString(7, entidade.getRespostaTexto());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Quantidade inesperada de registros criados.");
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("ID do check-in não retornado.");
                        }
                        entidade.setId(keys.getInt(1));
                        if (keys.wasNull() || entidade.getId() <= 0) {
                            throw new SQLException("ID do check-in inválido.");
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT data_checkin FROM CHECKIN WHERE id_checkin = ?")) {
                    ps.setInt(1, entidade.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getTimestamp("data_checkin") == null) {
                            throw new SQLException("Data do check-in não retornada.");
                        }
                        entidade.setDataCheckin(rs.getTimestamp("data_checkin").toLocalDateTime());
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


    @Override
    public void inserir(Checkin entidade) {
        String sql = "insert into CHECKIN(id_beneficiario, canal, nivel_estresse, " +
                "qualidade_sono , qualidade_alimentacao , humor , resposta_texto) values (? , ? , ? ,?, ?, ?, ?)";
        try(Connection connection = connectionFactory.conectar();
            PreparedStatement ps = connection.prepareStatement(sql)){
            ps.setInt(1,entidade.getBeneficiario().getId());
            ps.setString(2,entidade.getCanal());
            ps.setInt(3,entidade.getNivelEstresse());
            ps.setString(4,entidade.getQualidadeSono());
            ps.setString(5,entidade.getQualidadeAlimentacao());
            ps.setString(6,entidade.getHumor());
            ps.setString(7,entidade.getRespostaTexto());
            ps.execute();

        }catch (SQLException e){
            System.out.println(e.getMessage());
        }

    }

    @Override
    public List<Checkin> listar() {
        List<Checkin> lista = new ArrayList<>();
        String sql = "select * from CHECKIN";
        try(Connection connection = connectionFactory.conectar();
        PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()) {
            while (rs.next()){
                Checkin checkin = new Checkin();
                Beneficiario beneficiario = new Beneficiario();
                checkin.setId(rs.getInt("id_checkin"));
                beneficiario.setId(rs.getInt("id_beneficiario"));
                checkin.setBeneficiario(beneficiario);
                checkin.setDataCheckin(rs.getTimestamp("data_checkin").toLocalDateTime());
                checkin.setCanal(rs.getString("canal"));
                checkin.setNivelEstresse(rs.getInt("nivel_estresse"));
                checkin.setQualidadeSono(rs.getString("qualidade_sono"));
                checkin.setQualidadeAlimentacao(rs.getString("qualidade_alimentacao"));
                checkin.setHumor(rs.getString("humor"));
                checkin.setRespostaTexto(rs.getString("resposta_texto"));
                lista.add(checkin);

            }
        }catch (SQLException e){
            System.out.println(e.getMessage());
        }
        return lista;
    }
}
