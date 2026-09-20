package br.fiap.daiji.service;

import br.fiap.daiji.dao.AutenticacaoDAO;
import br.fiap.daiji.dao.BeneficiarioDAO;
import br.fiap.daiji.dto.LoginRequest;
import br.fiap.daiji.dto.LoginResponse;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AutenticacaoDAO autenticacaoDAO;
    private final BeneficiarioDAO beneficiarioDAO;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(AutenticacaoDAO autenticacaoDAO, BeneficiarioDAO beneficiarioDAO) {
        this.autenticacaoDAO = autenticacaoDAO;
        this.beneficiarioDAO = beneficiarioDAO;
    }

    public LoginResponse login(LoginRequest request) throws SQLException {
        if (request.email() == null || request.email().isBlank()
                || request.senha() == null || request.senha().isBlank()
                || request.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new CredenciaisInvalidasException();
        }
        var autenticacao = autenticacaoDAO.buscarPorEmail(request.email())
                .orElseThrow(CredenciaisInvalidasException::new);
        if (!"S".equals(autenticacao.ativo())
                || !("BENEFICIARIO".equals(autenticacao.tipoUsuario())
                     || "GESTOR".equals(autenticacao.tipoUsuario()))
                || !encoder.matches(request.senha(), autenticacao.senhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        Integer idBeneficiario = null;
        String nome = null;
        if ("BENEFICIARIO".equals(autenticacao.tipoUsuario())) {
            idBeneficiario = autenticacao.idBeneficiario();
            if (idBeneficiario == null) {
                throw new CredenciaisInvalidasException();
            }
            nome = beneficiarioDAO.buscarPorId(idBeneficiario)
                    .orElseThrow(CredenciaisInvalidasException::new).getNome();
        }
        return new LoginResponse(autenticacao.idAutenticacao(), idBeneficiario,
                nome, autenticacao.email(), autenticacao.tipoUsuario());
    }
}
