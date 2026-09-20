package br.fiap.daiji.telegram;

/** Saída independente do mecanismo usado para receber mensagens. */
public interface TelegramClient {
    void sendMessage(long chatId, String text) throws InterruptedException;
}
