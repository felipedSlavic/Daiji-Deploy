package br.fiap.daiji.service;

import br.fiap.daiji.dao.BeneficiarioDAO;
import br.fiap.daiji.dao.CheckinDAO;
import br.fiap.daiji.dto.CheckinRequest;
import br.fiap.daiji.dto.CheckinResponse;
import br.fiap.daiji.dto.CheckinResumo;
import br.fiap.daiji.model.Checkin;
import br.fiap.daiji.model.CanalCheckin;
import br.fiap.daiji.model.Beneficiario;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckinService {
    private final BeneficiarioDAO beneficiarioDAO;
    private final CheckinDAO checkinDAO;

    public CheckinService(BeneficiarioDAO beneficiarioDAO, CheckinDAO checkinDAO) {
        this.beneficiarioDAO = beneficiarioDAO;
        this.checkinDAO = checkinDAO;
    }

    public List<CheckinResumo> recentes(Integer idBeneficiario) throws SQLException {
        if (idBeneficiario == null || idBeneficiario < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Beneficiário inválido.");
        }
        beneficiarioDAO.buscarPorId(idBeneficiario).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Beneficiário não encontrado."));
        return checkinDAO.recentes(idBeneficiario);
    }

    public CheckinResponse criar(Integer idBeneficiario, CheckinRequest request) throws SQLException {
        return criar(idBeneficiario, request, CanalCheckin.APP);
    }

    public Beneficiario validarBeneficiario(Integer idBeneficiario) throws SQLException {
        if (idBeneficiario == null || idBeneficiario < 1 || idBeneficiario > 999999999)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Beneficiário inválido.");
        return beneficiarioDAO.buscarPorId(idBeneficiario).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Beneficiário não encontrado."));
    }

    public CheckinResponse criar(Integer idBeneficiario, CheckinRequest request, CanalCheckin canal) throws SQLException {
        if (idBeneficiario == null || idBeneficiario < 1 || idBeneficiario > 999999999 || request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Beneficiário ou corpo inválido.");
        }
        if (canal == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canal inválido.");
        validarRequest(request);
        var beneficiario = validarBeneficiario(idBeneficiario);
        Checkin checkin = new Checkin();
        checkin.setBeneficiario(beneficiario);
        checkin.setCanal(canal.name());
        checkin.setNivelEstresse(request.nivelEstresse());
        checkin.setQualidadeSono(normalizar(request.qualidadeSono()));
        checkin.setQualidadeAlimentacao(normalizar(request.qualidadeAlimentacao()));
        checkin.setHumor(normalizar(request.humor()));
        checkin.setRespostaTexto(normalizar(request.respostaTexto()));
        return CheckinResponse.from(checkinDAO.criar(checkin));
    }

    public static void validarRequest(CheckinRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Corpo inválido.");
        if (request.nivelEstresse() != null && (request.nivelEstresse() < 1 || request.nivelEstresse() > 5))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nível de estresse deve estar entre 1 e 5.");
        validarTexto(request.qualidadeSono(), 20);
        validarTexto(request.qualidadeAlimentacao(), 30);
        validarTexto(request.humor(), 20);
        validarTexto(request.respostaTexto(), 500);
    }

    private static void validarTexto(String texto, int limite) {
        // Limite conservador para VARCHAR2 com semântica BYTE em banco UTF-8.
        if (texto != null && texto.getBytes(StandardCharsets.UTF_8).length > limite) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Texto excede o limite do campo.");
        }
    }

    private static String normalizar(String texto) {
        // Oracle armazena a string vazia como NULL.
        return texto == null || texto.isEmpty() ? null : texto;
    }
}
