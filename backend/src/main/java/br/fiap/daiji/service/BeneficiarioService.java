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

    public Optional<Beneficiario> buscarPorEmail(String email) throws SQLException {
        String normalized = email == null ? "" : email.strip();
        if (normalized.length() > 254 || !normalized.matches("[^\\s@]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        return beneficiarioDAO.buscarPorEmail(normalized);
    }
}
