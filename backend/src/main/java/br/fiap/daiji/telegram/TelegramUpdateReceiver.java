package br.fiap.daiji.telegram;

import br.fiap.daiji.assistant.MessageRouter;
import br.fiap.daiji.assistant.ResponseFormatter;
import br.fiap.daiji.assistant.BeneficiaryJourney;

/** Adaptador reutilizável por polling hoje e por um controller webhook futuro. */
public class TelegramUpdateReceiver {
    private final MessageRouter router;
    private final ResponseFormatter formatter;
    private final TelegramClient client;
    private final BeneficiaryJourney journey;
    public TelegramUpdateReceiver(MessageRouter router, ResponseFormatter formatter, TelegramClient client) {
        this(router,formatter,client,null);
    }
    public TelegramUpdateReceiver(MessageRouter router, ResponseFormatter formatter, TelegramClient client, BeneficiaryJourney journey) {
        this.router = router;
        this.formatter = formatter;
        this.client = client;
        this.journey = journey;
    }
    public void receive(TelegramUpdate update) throws InterruptedException {
        if (update == null || update.message() == null) return;
        var message = update.message();
        if (message.text() == null || message.chat() == null || message.chat().id() == null
                || !"private".equals(message.chat().type())) return;
        client.sendMessage(message.chat().id(), formatter.format(journey == null ? router.route(message.text())
                : journey.route(message.chat().id(),message.text())));
    }
}
