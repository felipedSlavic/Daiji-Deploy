package br.fiap.daiji.gemini;

import br.fiap.daiji.assistant.ScoreFacts;

/** Não possui DAO, Connection, Telegram ou ferramentas executáveis. */
public interface GeminiClient {
    String interpret(String text) throws InterruptedException;
    String interpretJourney(String text) throws InterruptedException;
    String explain(ScoreFacts facts) throws InterruptedException;
}
