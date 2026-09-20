package br.fiap.daiji.dto;

import java.time.LocalDateTime;

public record ConfirmacaoMedicacaoResponse(long idConfirmacao, int idBeneficiario, int idMedicacao,
                                          LocalDateTime dataConfirmacao, String statusConfirmacao) {}
