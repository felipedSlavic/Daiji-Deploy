package br.fiap.daiji.dto;

import java.time.LocalDateTime;

public record EncaminhamentoResponse(long idEncaminhamento, int idBeneficiario,
        String nomeBeneficiario, int idProfissional, String nomeProfissional, String especialidade,
        LocalDateTime dataEncaminhamento, LocalDateTime dataConsulta, String statusEncaminhamento) { }
