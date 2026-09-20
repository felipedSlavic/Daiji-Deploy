package br.fiap.daiji.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

public record ConfirmacaoMedicacaoRequest(String statusConfirmacao) {
    // IDs vêm exclusivamente da URL; também rejeita foto/data e demais campos fora do contrato.
    @JsonAnySetter
    public void rejeitarCampo(String nome, JsonNode valor) {
        throw new IllegalArgumentException("Campo não permitido neste contrato.");
    }
}
