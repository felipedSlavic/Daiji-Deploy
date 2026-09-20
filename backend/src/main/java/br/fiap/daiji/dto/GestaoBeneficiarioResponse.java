package br.fiap.daiji.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GestaoBeneficiarioResponse(int idBeneficiario, int idEmpresa, String nome,
        String email, String statusAtivo, BigDecimal valorScore,
        String classificacaoRisco, LocalDateTime dataCalculoScore) { }
