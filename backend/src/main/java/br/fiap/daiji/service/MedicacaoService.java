package br.fiap.daiji.service;

import br.fiap.daiji.dao.BeneficiarioDAO;
import br.fiap.daiji.dao.MedicacaoDAO;
import br.fiap.daiji.dto.MedicacaoRequest;
import br.fiap.daiji.dto.MedicacaoResponse;
import br.fiap.daiji.model.Beneficiario;
import br.fiap.daiji.model.Medicacao;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicacaoService {
    private final BeneficiarioDAO beneficiarioDAO;
    private final MedicacaoDAO medicacaoDAO;

    public MedicacaoService(BeneficiarioDAO beneficiarioDAO, MedicacaoDAO medicacaoDAO) {
        this.beneficiarioDAO = beneficiarioDAO;
        this.medicacaoDAO = medicacaoDAO;
    }

    public MedicacaoResponse criar(Integer idBeneficiario, MedicacaoRequest request) throws SQLException {
        if (request == null || request.nomeMedicamento() == null || request.nomeMedicamento().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do medicamento obrigatório.");
        }
        validarTexto(request.nomeMedicamento(), 100);
        validarTexto(request.dosagem(), 50);
        validarTexto(request.horarioPrevisto(), 5);
        Beneficiario beneficiario = buscarBeneficiario(idBeneficiario);
        Medicacao medicacao = new Medicacao();
        medicacao.setBeneficiario(beneficiario);
        medicacao.setNome(request.nomeMedicamento());
        medicacao.setDosagem(normalizar(request.dosagem()));
        medicacao.setHorario(normalizar(request.horarioPrevisto()));
        return MedicacaoResponse.from(medicacaoDAO.criar(medicacao));
    }

    public List<MedicacaoResponse> listar(Integer idBeneficiario) throws SQLException {
        buscarBeneficiario(idBeneficiario);
        return medicacaoDAO.listarPorBeneficiario(idBeneficiario).stream()
                .map(MedicacaoResponse::from).toList();
    }

    private Beneficiario buscarBeneficiario(Integer id) throws SQLException {
        if (id == null || id < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Beneficiário inválido.");
        }
        return beneficiarioDAO.buscarPorId(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Beneficiário não encontrado."));
    }

    private static void validarTexto(String texto, int limite) {
        // Mesma política conservadora do CP03 para VARCHAR2 BYTE em UTF-8.
        if (texto != null && texto.getBytes(StandardCharsets.UTF_8).length > limite) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Texto excede o limite do campo.");
        }
    }

    private static String normalizar(String texto) {
        return texto == null || texto.isEmpty() ? null : texto;
    }
}
