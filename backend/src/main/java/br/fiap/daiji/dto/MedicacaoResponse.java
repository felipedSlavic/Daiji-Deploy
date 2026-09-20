package br.fiap.daiji.dto;

import br.fiap.daiji.model.Medicacao;

public record MedicacaoResponse(Integer idMedicacao, Integer idBeneficiario,
                                String nomeMedicamento, String dosagem, String horarioPrevisto) {
    public static MedicacaoResponse from(Medicacao medicacao) {
        return new MedicacaoResponse(medicacao.getId(), medicacao.getBeneficiario().getId(),
                medicacao.getNome(), medicacao.getDosagem(), medicacao.getHorario());
    }
}
