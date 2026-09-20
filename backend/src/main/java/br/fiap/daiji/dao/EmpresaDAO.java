package br.fiap.daiji.dao;

import br.fiap.daiji.factory.ConnectionFactory;
import br.fiap.daiji.model.Empresa;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class EmpresaDAO  implements GenericDAO<Empresa , Integer> {

    private final ConnectionFactory connectionFactory;

    public EmpresaDAO(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }



    @Override
    public void inserir(Empresa entidade) {
        String sql = "insert into EMPRESA(cnpj, razao_social) values(?, ?)";
        try(Connection connection = connectionFactory.conectar();
            PreparedStatement ps = connection.prepareStatement(sql)){
            ps.setString(1,entidade.getCnpj());
            ps.setString(2,entidade.getRazaoSocial());
            ps.execute();
        }
        catch (SQLException e){
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Empresa> listar() {
        List<Empresa> lista = new ArrayList<>();
        String sql = "select * from EMPRESA";
        try(Connection connection = connectionFactory.conectar();
        PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()){

            while (rs.next()){
                Empresa empresa = new Empresa();
                empresa.setId(rs.getInt("id_empresa"));
                empresa.setCnpj(rs.getString("cnpj"));
                empresa.setRazaoSocial((rs.getString("razao_social")));
                empresa.setDataCadastro(rs.getDate("data_cadastro").toLocalDate());
                lista.add(empresa);
            }

        } catch (SQLException e){
            System.out.println(e.getMessage());
        }
        return lista;
    }
}
