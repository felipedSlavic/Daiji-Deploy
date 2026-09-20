package br.fiap.daiji.service;

import br.fiap.daiji.dao.BeneficiarioDAO;
import br.fiap.daiji.dao.EncaminhamentoDAO;
import br.fiap.daiji.dao.ProfissionalDAO;
import br.fiap.daiji.dto.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EncaminhamentoService {
    private static final Set<String> STATUS = Set.of("PENDENTE", "AGENDADO", "REALIZADO", "CANCELADO");
    private final EncaminhamentoDAO encaminhamentos;
    private final BeneficiarioDAO beneficiarios;
    private final ProfissionalDAO profissionais;
    private final GestaoService gestao;

    public EncaminhamentoService(EncaminhamentoDAO encaminhamentos, BeneficiarioDAO beneficiarios,
                                  ProfissionalDAO profissionais, GestaoService gestao) {
        this.encaminhamentos = encaminhamentos;
        this.beneficiarios = beneficiarios;
        this.profissionais = profissionais;
        this.gestao = gestao;
    }

    public EncaminhamentoResponse criar(Integer id, EncaminhamentoRequest request) throws SQLException {
        positivo(id);
        if (request == null) throw invalido("Body obrigatório.");
        positivo(request.idProfissional());
        LocalDateTime consulta = data(request.dataConsulta());
        if (beneficiarios.buscarPorId(id).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Beneficiário não encontrado.");
        if (!profissionais.existe(request.idProfissional()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado.");
        return encaminhamentos.criar(id, request.idProfissional(), consulta);
    }

    public List<EncaminhamentoResponse> listar(Integer empresa, String status) throws SQLException {
        if (status != null) validarStatus(status);
        gestao.validarEmpresa(empresa);
        return encaminhamentos.listar(empresa, status);
    }

    public EncaminhamentoResponse atualizar(Long id, EncaminhamentoUpdateRequest request) throws SQLException {
        positivo(id);
        if (request == null) throw invalido("Body obrigatório.");
        validarStatus(request.statusEncaminhamento());
        LocalDateTime consulta = data(request.dataConsulta());
        if ("AGENDADO".equals(request.statusEncaminhamento()) && consulta == null)
            throw invalido("Data da consulta obrigatória para AGENDADO.");
        return encaminhamentos.atualizar(id, request.statusEncaminhamento(), consulta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Encaminhamento não encontrado."));
    }

    private static void positivo(Number id) {
        if (id == null || id.longValue() < 1) throw invalido("Identificador obrigatório e positivo.");
    }

    private static void validarStatus(String status) {
        if (status == null || !STATUS.contains(status)) throw invalido("Status de encaminhamento inválido.");
    }

    private static LocalDateTime data(LocalDateTime data) {
        if (data == null) return null;
        if (data.getYear() < 1 || data.getYear() > 9999) throw invalido("Data da consulta inválida.");
        return data.truncatedTo(ChronoUnit.SECONDS);
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
