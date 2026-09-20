package br.fiap.daiji.dto;

import br.fiap.daiji.model.Checkin;
import java.time.LocalDateTime;

public record CheckinResponse(Integer idCheckin, Integer idBeneficiario,
                              LocalDateTime dataCheckin, String canal, Integer nivelEstresse,
                              String qualidadeSono, String qualidadeAlimentacao,
                              String humor, String respostaTexto) {
    public static CheckinResponse from(Checkin checkin) {
        return new CheckinResponse(checkin.getId(), checkin.getBeneficiario().getId(),
                checkin.getDataCheckin(), checkin.getCanal(), checkin.getNivelEstresse(),
                checkin.getQualidadeSono(), checkin.getQualidadeAlimentacao(),
                checkin.getHumor(), checkin.getRespostaTexto());
    }
}
