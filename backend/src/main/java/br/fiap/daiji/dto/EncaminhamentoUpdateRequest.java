package br.fiap.daiji.dto;

import java.time.LocalDateTime;

public record EncaminhamentoUpdateRequest(String statusEncaminhamento, LocalDateTime dataConsulta) { }
