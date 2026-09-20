package br.fiap.daiji.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public record EncaminhamentoRequest(
        @JsonDeserialize(using = CheckinRequest.InteiroEstrito.class) Integer idProfissional,
        LocalDateTime dataConsulta) { }
