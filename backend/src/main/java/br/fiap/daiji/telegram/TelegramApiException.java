package br.fiap.daiji.telegram;

/** Não guarda causa, URL, corpo HTTP ou descrição recebida do servidor. */
public class TelegramApiException extends RuntimeException {
    private final int status;
    private final int retryAfter;
    public TelegramApiException(int status, int retryAfter) {
        super("Falha na comunicação com Telegram.");
        this.status = status;
        this.retryAfter = Math.clamp(retryAfter, 0, 300);
    }
    public boolean invalidCredential() { return status == 401 || status == 404; }
    public int retryAfter() { return retryAfter; }
}
