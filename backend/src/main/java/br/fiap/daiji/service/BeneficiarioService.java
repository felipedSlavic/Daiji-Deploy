package br.fiap.daiji.service;

import br.fiap.daiji.dao.BeneficiarioDAO;
import br.fiap.daiji.model.Beneficiario;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BeneficiarioService {
    private final BeneficiarioDAO beneficiarioDAO;

    public BeneficiarioService(BeneficiarioDAO beneficiarioDAO) {
        this.beneficiarioDAO = beneficiarioDAO;
    }

    public Optional<Beneficiario> buscarPorId(Integer id) throws SQLException {
        return beneficiarioDAO.buscarPorId(id);
    }
}
