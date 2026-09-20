package br.fiap.daiji.telegram;

import java.util.List;

public interface TelegramUpdateSource {
    List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) throws InterruptedException;
}
