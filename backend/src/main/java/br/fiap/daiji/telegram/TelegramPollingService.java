package br.fiap.daiji.telegram;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

public class TelegramPollingService implements SmartLifecycle {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramPollingService.class);
    @FunctionalInterface public interface Pause { void await(long millis) throws InterruptedException; }
    private final TelegramUpdateSource source;
    private final TelegramUpdateReceiver receiver;
    private final Pause pause;
    private final int timeout;
    private final boolean configured;
    private volatile boolean running;
    private ExecutorService worker;
    private long offset;
    private long backoff = 1000;
    public TelegramPollingService(TelegramUpdateSource source, TelegramUpdateReceiver receiver, int timeout,
                                  boolean configured, Pause pause) {
        this.source = source;
        this.receiver = receiver;
        this.timeout = timeout;
        this.configured = configured;
        this.pause = pause;
    }
    @Override public synchronized void start() {
        if (running || (worker != null && !worker.isTerminated())) return;
        if (!configured) {
            LOG.warn("Telegram desativado: configure TELEGRAM_BOT_TOKEN válido no ambiente.");
            return;
        }
        running = true;
        worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("daiji-telegram-polling").daemon(true).factory());
        ExecutorService currentWorker = worker;
        worker.submit(() -> {
            try {
                while (running && !Thread.currentThread().isInterrupted()) pollOnce();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { running = false; currentWorker.shutdown(); }
        });
    }
    /** Uma iteração determinística; invocada somente pelo worker em produção. */
    void pollOnce() throws InterruptedException {
        try {
            var updates = source.getUpdates(offset, timeout).stream().filter(Objects::nonNull)
                    .filter(u -> u.updateId() != null && u.updateId() >= 0 && u.updateId() < Long.MAX_VALUE)
                    .sorted(Comparator.comparingLong(TelegramUpdate::updateId)).toList();
            for (var update : updates) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (update.updateId() < offset) continue;
                try { receiver.receive(update); }
                catch (InterruptedException e) { throw e; }
                catch (RuntimeException e) {
                    LOG.warn("Falha ao responder update Telegram; processamento continuará.");
                    // Não reenvia uma resposta cujo envio pode ter sido aceito pelo servidor.
                    if (e instanceof TelegramApiException api && api.invalidCredential()) throw api;
                    pause.await(e instanceof TelegramApiException api && api.retryAfter() > 0
                            ? api.retryAfter() * 1000L : 1000);
                } finally { offset = update.updateId() + 1; }
            }
            backoff = 1000;
            // Também protege contra respostas vazias imediatas/repetidas do servidor.
            pause.await(250);
        } catch (RuntimeException e) {
            if (e instanceof TelegramApiException api && api.invalidCredential()) {
                LOG.warn("Telegram desativado: credencial recusada. Corrija o ambiente e reinicie a aplicação.");
                running = false;
                return;
            }
            LOG.warn("Telegram indisponível; nova tentativa após espera.");
            long delay = e instanceof TelegramApiException api ? Math.max(backoff, api.retryAfter() * 1000L) : backoff;
            pause.await(delay);
            backoff = Math.min(backoff * 2, 30000);
        }
    }
    @Override public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.shutdownNow();
            try {
                if (!worker.awaitTermination(5, TimeUnit.SECONDS)) LOG.warn("Worker Telegram ainda encerrando uma operação em andamento.");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
    @Override public void stop(Runnable callback) { stop(); callback.run(); }
    @Override public boolean isRunning() { return running; }
}
