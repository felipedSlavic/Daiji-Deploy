package br.fiap.daiji.service;

import br.fiap.daiji.dao.BeneficiarioDAO;
import br.fiap.daiji.dao.ConfirmacaoMedicacaoDAO;
import br.fiap.daiji.dto.*;
import br.fiap.daiji.model.ConfirmacaoStatus;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConfirmacaoMedicacaoService {
    private final BeneficiarioDAO beneficiarios;
    private final ConfirmacaoMedicacaoDAO confirmacoes;
    public ConfirmacaoMedicacaoService(BeneficiarioDAO beneficiarios, ConfirmacaoMedicacaoDAO confirmacoes) {
        this.beneficiarios = beneficiarios; this.confirmacoes = confirmacoes;
    }
    public ConfirmacaoMedicacaoResponse criar(Integer beneficiario, Integer medicacao, ConfirmacaoMedicacaoRequest request) throws SQLException {
        if (beneficiario == null || beneficiario < 1 || medicacao == null || medicacao < 1 || request == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificadores ou corpo inválidos.");
        ConfirmacaoStatus status;
        try { status = ConfirmacaoStatus.valueOf(request.statusConfirmacao()); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de confirmação inválido."); }
        beneficiarios.buscarPorId(beneficiario).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Beneficiário não encontrado."));
        return confirmacoes.criar(beneficiario, medicacao, status).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Medicação não encontrada para este beneficiário."));
    }
}
