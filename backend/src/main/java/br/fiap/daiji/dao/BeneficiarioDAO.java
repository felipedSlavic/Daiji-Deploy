package br.fiap.daiji.dao;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.model.Empresa;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;



@org.springframework.stereotype.Repository
public class BeneficiarioDAO  implements GenericDAO<Beneficiario , Integer>{

    private final ConnectionFactory connectionFactory;

    public BeneficiarioDAO(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public java.util.Optional<Beneficiario> buscarPorId(Integer id) throws SQLException {
        String sql = """
                SELECT id_beneficiario, id_empresa, nome, cpf, email, data_nascimento,
                       telefone_whatsapp, status_ativo, data_cadastro
                FROM BENEFICIARIO
                WHERE id_beneficiario = ?
                """;
        try (Connection connection = connectionFactory.conectar();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Optional.empty();
                }
                Beneficiario beneficiario = new Beneficiario();
                beneficiario.setId(rs.getInt("id_beneficiario"));
                Empresa empresa = new Empresa();
                empresa.setId(rs.getInt("id_empresa"));
                beneficiario.setEmpresa(empresa);
                beneficiario.setNome(rs.getString("nome"));
                beneficiario.setCpf(rs.getString("cpf"));
                beneficiario.setEmail(rs.getString("email"));
                beneficiario.setDataNascimento(rs.getDate("data_nascimento").toLocalDate());
                beneficiario.setTelefone(rs.getString("telefone_whatsapp"));
                beneficiario.setStatus(rs.getString("status_ativo"));
                beneficiario.setDataCadastro(rs.getDate("data_cadastro").toLocalDate());
                return java.util.Optional.of(beneficiario);
            }
        }
    }


    @Override
    public void inserir(Beneficiario entidade) {
        String sql = "insert into BENEFICIARIO(id_empresa, nome , cpf, email , data_nascimento , " +
                "telefone_whatsapp, status_ativo) values(?, ? ,?, ?, ?, ?, ?)";
        try(Connection connection = connectionFactory.conectar();
            PreparedStatement ps = connection.prepareStatement(sql)){
            ps.setInt(1,entidade.getEmpresa().getId());
            ps.setString(2,entidade.getNome());
            ps.setString(3,entidade.getCpf());
            ps.setString(4,entidade.getEmail());
            ps.setDate(5, Date.valueOf(entidade.getDataNascimento()));
            ps.setString(6, entidade.getTelefone());
            ps.setString(7,entidade.getStatus());
            ps.execute();
        }catch (SQLException e){
            System.out.println(e.getMessage());
        }

    }

    @Override
    public List<Beneficiario> listar() {
        List<Beneficiario> lista = new ArrayList<>();
        String sql = "select * from  BENEFICIARIO";
        try(Connection connection = connectionFactory.conectar();
            PreparedStatement ps = connection.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
            while (rs.next()){
                Beneficiario beneficiario = new Beneficiario();
                beneficiario.setId(rs.getInt("id_beneficiario"));
                Empresa empresa = new Empresa();
                empresa.setId(rs.getInt("id_empresa"));
                beneficiario.setEmpresa(empresa);
                beneficiario.setNome(rs.getString("nome"));
                beneficiario.setCpf(rs.getString("cpf"));
                beneficiario.setEmail(rs.getString("email"));
                beneficiario.setDataNascimento(rs.getDate("data_nascimento").toLocalDate());
                beneficiario.setTelefone(rs.getString("telefone_whatsapp"));
                beneficiario.setStatus(rs.getString("status_ativo"));
                beneficiario.setDataCadastro(rs.getDate("data_cadastro").toLocalDate());
                lista.add(beneficiario);
            }
        } catch (SQLException e){
            if(e.getErrorCode() == 2292){
                System.out.println("Não é possível exluir este beneficiário ,pois existem registros filhos vinculados");
            }
            System.out.println(e.getMessage());
        }
        return lista;
    }

    public void atualizar(Beneficiario beneficiario){
        String sql = "update BENEFICIARIO set nome = ?, cpf = ?, email = ?, " +
                "data_nascimento = ?, telefone_whatsapp = ?, status_ativo = ? where id_beneficiario = ?";
        try(Connection connection = connectionFactory.conectar();
            PreparedStatement ps = connection.prepareStatement(sql)){
            ps.setString(1,beneficiario.getNome());
            ps.setString(2,beneficiario.getCpf());
            ps.setString(3,beneficiario.getEmail());
            ps.setDate(4, Date.valueOf(beneficiario.getDataNascimento()));
            ps.setString(5, beneficiario.getTelefone());
            ps.setString(6,beneficiario.getStatus());
            ps.setInt(7,beneficiario.getId());
            ps.execute();
        }catch (SQLException e){
            System.out.println(e.getMessage());
        }
    }



    public void excluir(Beneficiario beneficiario){
        String sql = "delete from BENEFICIARIO where id_beneficiario = ?";
        try(Connection connection = connectionFactory.conectar();
        PreparedStatement ps = connection.prepareStatement(sql)){
            ps.setInt(1,beneficiario.getId());
            ps.execute();
        }catch (SQLException e){
            System.out.println(e.getMessage());
        }
    }
}
