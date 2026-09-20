package br.fiap.daiji.assistant;

import br.fiap.daiji.dto.ConfirmacaoMedicacaoRequest;
import br.fiap.daiji.model.ConfirmacaoStatus;
import br.fiap.daiji.service.ConfirmacaoMedicacaoService;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Orquestra o canal conversacional; regras de escrita permanecem nos Services compartilhados. */
@Component
public class BeneficiaryJourney {
    public static final String HELP = "Jornada do beneficiário:\n/checkin <idBeneficiario>\n/cancelar\n"
            + "/confirmar <idBeneficiario> <idMedicacao> PENDENTE|CONFIRMADO|PERDIDO\n\n"
            + "Use /medicacoes_ids <idBeneficiario> para consultar os IDs das medicações e /ajuda para os comandos de consulta.";
    public static final String CONFIRM_ERROR = "Não foi possível confirmar o registro agora. Não repita automaticamente: verifique o registro antes de tentar novamente.";
    private final CheckinConversations conversations;
    private final JourneyNaturalLanguage natural;
    private final ConfirmacaoMedicacaoService confirmations;
    private final MessageRouter router;
    public BeneficiaryJourney(CheckinConversations conversations, JourneyNaturalLanguage natural,
                              ConfirmacaoMedicacaoService confirmations, MessageRouter router) {
        this.conversations = conversations; this.natural = natural; this.confirmations = confirmations; this.router = router;
    }
    public AssistantResponse route(long conversationId, String text) {
        var active = conversations.handle(conversationId,text);
        if (active.isPresent()) return active.get();
        String value = text == null ? "" : text.strip();
        String[] parts = value.split("\\s+");
        String command = parts[0].split("@",2)[0].toLowerCase(Locale.ROOT);
        if (command.equals("/jornada")) return new AssistantResponse.Text(HELP);
        if (command.equals("/checkin")) {
            try {
                if (parts.length != 2) throw new IllegalArgumentException();
                int id = positive(parts[1]); if (id > 999999999) throw new IllegalArgumentException();
                return conversations.start(conversationId,id);
            } catch (IllegalArgumentException e) { return new AssistantResponse.Text(JourneyNaturalLanguage.CHECKIN_FORMAT); }
        }
        if (command.equals("/confirmar")) {
            try {
                if (parts.length != 4) throw new IllegalArgumentException();
                return confirm(new JourneyDecision.Confirm(positive(parts[1]),positive(parts[2]),
                        ConfirmacaoStatus.valueOf(parts[3].toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException e) { return new AssistantResponse.Text(JourneyNaturalLanguage.CLARIFY); }
        }
        if (!value.startsWith("/")) {
            var decision = natural.interpret(value);
            if (decision.isPresent()) return switch (decision.get()) {
                case JourneyDecision.Start start -> conversations.start(conversationId,start.beneficiario());
                case JourneyDecision.Confirm confirm -> confirm(confirm);
                case JourneyDecision.Reply reply -> new AssistantResponse.Text(reply.text());
            };
        }
        return router.route(text);
    }
    private AssistantResponse confirm(JourneyDecision.Confirm request) {
        try {
            confirmations.criar(request.beneficiario(),request.medicacao(),new ConfirmacaoMedicacaoRequest(request.status().name()));
            return new AssistantResponse.Text("Confirmação registrada com sucesso.");
        } catch (ResponseStatusException e) {
            return new AssistantResponse.Text(e.getStatusCode().value() == 404
                    ? "Beneficiário ou medicação não encontrado para os IDs informados." : JourneyNaturalLanguage.CLARIFY);
        } catch (Exception e) { return new AssistantResponse.Text(CONFIRM_ERROR); }
    }
    private static int positive(String value) {
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
        int id = Integer.parseInt(value); if (id < 1) throw new IllegalArgumentException(); return id;
    }
}
