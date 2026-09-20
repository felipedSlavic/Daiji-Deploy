package br.fiap.daiji.assistant;

import java.math.BigDecimal;

/** Projeção exclusiva para explicação; nenhum identificador ou dado pessoal. */
public record ScoreFacts(BigDecimal valorScore, String classificacaoRisco,
                         boolean fatoresHistoricosDisponiveis) {
    public static ScoreFacts from(AssistantResponse.Score score) {
        return new ScoreFacts(score.valor(), score.classificacao(), false);
    }
}
