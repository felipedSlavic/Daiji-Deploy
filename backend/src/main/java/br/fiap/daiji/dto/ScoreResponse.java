package br.fiap.daiji.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScoreResponse(long idScore, int idBeneficiario, LocalDateTime dataCalculo,
                            BigDecimal valorScore, String classificacaoRisco,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Fatores fatores) {
    public record Fatores(int hipertensao, int diabetes, int outrasComorbidades, int idade,
                          int adesaoMedicacao, int checkins,
                          boolean dadosAdesaoSuficientes, boolean dadosCheckinsSuficientes) {
        public int total() {
            return hipertensao + diabetes + outrasComorbidades + idade + adesaoMedicacao + checkins;
        }
    }
}
