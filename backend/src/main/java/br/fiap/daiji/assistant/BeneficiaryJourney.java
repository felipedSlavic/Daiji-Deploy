package br.fiap.daiji.assistant;

import br.fiap.daiji.dto.ConfirmacaoMedicacaoRequest;
import br.fiap.daiji.model.ConfirmacaoStatus;
import br.fiap.daiji.service.ConfirmacaoMedicacaoService;
import br.fiap.daiji.service.BeneficiarioService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Orquestra o canal conversacional; regras de escrita permanecem nos Services compartilhados. */
@Component
public class BeneficiaryJourney {
    public static final String HELP = "Jornada do beneficiário:\n/checkin <email>\n/cancelar\n"
            + "/confirmar <email> <idMedicacao> PENDENTE|CONFIRMADO|PERDIDO\n\n"
            + "Use /medicacoes_ids <email> para consultar os IDs das medicações e /ajuda para os comandos de consulta.";
    public static final String ASK_EMAIL = "Claro! Para localizar seu cadastro, informe o e-mail utilizado na Daiji.";
    public static final String INVALID_EMAIL = "Esse e-mail não parece válido. Digite o e-mail utilizado no seu cadastro da Daiji.";
    public static final String EMAIL_NOT_FOUND = "Não encontrei um beneficiário cadastrado com esse e-mail. Confira o endereço informado e tente novamente.";
    private record Pending(IntFunction<AssistantResponse> action, Instant expires) {}
    private final Map<Long, Pending> pending = new HashMap<>();
    private final BeneficiarioService beneficiarios;
    public static final String CONFIRM_ERROR = "Não foi possível confirmar o registro agora. Não repita automaticamente: verifique o registro antes de tentar novamente.";
    private final CheckinConversations conversations;
    private final JourneyNaturalLanguage natural;
    private final ConfirmacaoMedicacaoService confirmations;
    private final MessageRouter router;
    public BeneficiaryJourney(CheckinConversations conversations, JourneyNaturalLanguage natural,
                              ConfirmacaoMedicacaoService confirmations, MessageRouter router, BeneficiarioService beneficiarios) {
        this.conversations = conversations; this.natural = natural; this.confirmations = confirmations; this.router = router;
        this.beneficiarios = beneficiarios;
    }
    /** Resolve a identificação no canal; somente o ID obtido pelo Service segue aos fluxos internos. */
    public synchronized AssistantResponse route(long conversationId, String text) {
        pending.entrySet().removeIf(entry -> !Instant.now().isBefore(entry.getValue().expires()));
        String value = text == null ? "" : text.strip();
        String[] parts = value.split("\\s+");
        String command = parts[0].split("@", 2)[0].toLowerCase(Locale.ROOT).replace("\\_", "_");
        if (command.equals("/cancelar") || value.equalsIgnoreCase("cancelar")) {
            if (pending.remove(conversationId) != null) return new AssistantResponse.Text("Identificação cancelada.");
        }
        var active = conversations.handle(conversationId, text);
        if (active.isPresent()) return active.get();
        if (value.startsWith("/")) pending.remove(conversationId);
        var waiting = pending.get(conversationId);
        if (waiting != null) return resolve(conversationId, value, waiting.action());
        if (Set.of("/checkin", "/score", "/medicacoes", "/medicacoes_ids", "/checkins", "/confirmar").contains(command)) {
            if (command.equals("/confirmar")) {
                try {
                    if (parts.length != 4) throw new IllegalArgumentException();
                    int medication = positive(parts[2]);
                    var status = ConfirmacaoStatus.valueOf(parts[3].toUpperCase(Locale.ROOT));
                    return resolve(conversationId, parts[1], id -> confirm(new JourneyDecision.Confirm(id, medication, status)));
                } catch (IllegalArgumentException e) { return new AssistantResponse.Text(JourneyNaturalLanguage.CLARIFY); }
            }
            IntFunction<AssistantResponse> action = command.equals("/checkin")
                    ? id -> conversations.start(conversationId, id) : id -> router.route(command + " " + id);
            if (parts.length == 1) return ask(conversationId, action);
            if (parts.length != 2) return new AssistantResponse.Text(INVALID_EMAIL);
            return resolve(conversationId, parts[1], action);
        }
        if (!value.startsWith("/")) {
            // O e-mail é resolvido localmente e removido antes de qualquer chamada ao Gemini.
            String email = null;
            for (String part : parts) if (part.contains("@")) {
                if (email != null) return new AssistantResponse.Text(INVALID_EMAIL);
                email = part.replaceAll("[.!?,;]+$", "");
            }
            String safe = email == null ? value : value.replace(email, "").strip();
            String normalized = AssistantSafety.normalize(safe).replaceAll("[.!?]+$", "").strip();
            if (AssistantSafety.blocked(safe)) return new AssistantResponse.Text(NaturalLanguageAssistant.OUT_OF_SCOPE);
            if (normalized.matches("(?s).*\\b(beneficiario|id)\\s*[:#]?\\s*[+-]?\\d+.*"))
                return new AssistantResponse.Text(ASK_EMAIL);
            if (normalized.matches("(?:quero )?(?:fazer|realizar|registrar|iniciar) (?:o |meu |um )?check[- ]?in(?: (?:do|de|para o))?[.!?]?")) {
                IntFunction<AssistantResponse> action = id -> conversations.start(conversationId, id);
                return email == null ? ask(conversationId, action) : resolve(conversationId, email, action);
            }
            if (email != null) {
                String template = value.replace(email, "{beneficiario}")
                        .replaceAll("(?i)(?:benefici[aá]rio|id)\\s+\\{beneficiario}", "{beneficiario}");
                return resolve(conversationId, email, id -> routeResolved(conversationId,
                        template.replace("{beneficiario}", "beneficiário " + id)));
            }
            if (normalized.matches(".*\\b(score|medicacoes|medicamentos|check-?ins)\\b.*")
                    && !normalized.matches(".*\\d.*")) {
                return ask(conversationId, id -> router.route(value + " do beneficiário " + id));
            }
        }
        return routeResolved(conversationId, text);
    }
    private AssistantResponse ask(long conversationId, IntFunction<AssistantResponse> action) {
        pending.put(conversationId, new Pending(action, Instant.now().plus(CheckinConversations.TTL)));
        return new AssistantResponse.Text(ASK_EMAIL);
    }
    private AssistantResponse resolve(long conversationId, String email, IntFunction<AssistantResponse> action) {
        pending.put(conversationId, new Pending(action, Instant.now().plus(CheckinConversations.TTL)));
        int id;
        try {
            var person = beneficiarios.buscarPorEmail(email);
            if (person.isEmpty()) return new AssistantResponse.Text(EMAIL_NOT_FOUND);
            id = person.get().getId();
        } catch (IllegalArgumentException e) { return new AssistantResponse.Text(INVALID_EMAIL); }
        catch (Exception e) { return new AssistantResponse.Text(MessageRouter.ERRO); }
        pending.remove(conversationId);
        return action.apply(id);
    }
    private AssistantResponse routeResolved(long conversationId, String text) {
        String value = text == null ? "" : text.strip();
        String[] parts = value.split("\\s+");
        String command = parts[0].split("@",2)[0].toLowerCase(Locale.ROOT);
        if (command.equals("/jornada")) return new AssistantResponse.Text(HELP);
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
                    ? "Beneficiário ou medicação não encontrado para os dados informados." : JourneyNaturalLanguage.CLARIFY);
        } catch (Exception e) { return new AssistantResponse.Text(CONFIRM_ERROR); }
    }
    private static int positive(String value) {
        if (!value.matches("[0-9]+")) throw new IllegalArgumentException();
        int id = Integer.parseInt(value); if (id < 1) throw new IllegalArgumentException(); return id;
    }
}
