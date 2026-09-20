package br.fiap.daiji.gemini;

/** Não preserva causa, header, URL, resposta remota nem credencial. */
public class GeminiException extends RuntimeException {
    public GeminiException() { super("Gemini temporariamente indisponível."); }
}
