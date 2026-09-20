package br.fiap.daiji.assistant;

public enum MessageIntent {
    CONSULTAR_SCORE, EXPLICAR_SCORE, CONSULTAR_MEDICACOES, CONSULTAR_CHECKINS,
    AJUDA, SAUDACAO, FORA_DE_ESCOPO;

    public boolean requiresId() {
        return this == CONSULTAR_SCORE || this == EXPLICAR_SCORE
                || this == CONSULTAR_MEDICACOES || this == CONSULTAR_CHECKINS;
    }
}
