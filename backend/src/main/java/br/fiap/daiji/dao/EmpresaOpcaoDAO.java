package br.fiap.daiji.dao;
import br.fiap.daiji.dto.EmpresaOpcaoResponse;
import br.fiap.daiji.factory.ConnectionFactory;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class EmpresaOpcaoDAO {
    private final ConnectionFactory factory;
    public EmpresaOpcaoDAO(ConnectionFactory factory) { this.factory=factory; }
    public List<EmpresaOpcaoResponse> listar() throws SQLException {
        try(Connection c=factory.conectar(); PreparedStatement ps=c.prepareStatement("SELECT id_empresa, razao_social FROM EMPRESA ORDER BY razao_social, id_empresa"); ResultSet rs=ps.executeQuery()) {
            var lista=new ArrayList<EmpresaOpcaoResponse>();
            while(rs.next()) lista.add(new EmpresaOpcaoResponse(rs.getInt("id_empresa"),rs.getString("razao_social")));
            return lista;
        }
    }
}
