package br.fiap.daiji.dto;

import java.math.BigDecimal;

public record DashboardResponse(Integer idEmpresa, long totalBeneficiarios,
        long beneficiariosComScore, long beneficiariosSemScore,
        long baixoRisco, long medioRisco, long altoRisco,
        BigDecimal scoreMedio, long encaminhamentosPendentes) { }
