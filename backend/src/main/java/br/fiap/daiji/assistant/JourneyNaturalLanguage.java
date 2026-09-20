package br.fiap.daiji.assistant;

import br.fiap.daiji.gemini.GeminiClient;
import br.fiap.daiji.model.ConfirmacaoStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class JourneyNaturalLanguage {
    public static final String IDENTIFY = "Informe o ID do beneficiário e da medicação.\nUse /medicacoes_ids <idBeneficiario> para consultar os IDs das medicações cadastradas.";
    public static final String CLARIFY = "Informe os IDs e o status explicitamente.\nUse /confirmar <idBeneficiario> <idMedicacao> PENDENTE|CONFIRMADO|PERDIDO.";
    public static final String CHECKIN_FORMAT = "Informe o ID do beneficiário.\nUse /checkin <idBeneficiario> ou: Quero fazer o check-in do beneficiário 1.";
    private static final Pattern CHECKIN = Pattern.compile("(?:quero )?(?:fazer|realizar|registrar|iniciar) (?:o )?check[- ]?in (?:do|para o) (?:beneficiario|id) ([0-9]+)[.!?]?");
    private static final Pattern CONFIRM = Pattern.compile("(?:quero )?confirmar (?:a )?medicacao ([0-9]+) do (?:beneficiario|id) ([0-9]+) (?:como|com status|status) (pendente|confirmado|perdido)[.!]?");
    private static final Pattern TOOK = Pattern.compile("(?:o )?beneficiario ([0-9]+) tomou (?:a )?medicacao ([0-9]+)[.!]?");
    private final Optional<GeminiClient> gemini;
    public JourneyNaturalLanguage(Optional<GeminiClient> gemini) { this.gemini = gemini; }

    public Optional<JourneyDecision> interpret(String input) {
        String text = AssistantSafety.normalize(input).replaceAll("\\s+", " ");
        boolean checkin = text.matches(".*\\b(fazer|realizar|registrar|iniciar)\\b.*check[- ]?in.*");
        boolean medication = text.matches(".*\\b(confirmar|tomei|tomou|registrar)\\b.*(medic|remedio).*");
        if (!checkin && !medication) return Optional.empty();
        if (AssistantSafety.blocked(input)) return Optional.of(new JourneyDecision.Reply(NaturalLanguageAssistant.OUT_OF_SCOPE));
        JourneyDecision expected;
        try {
            var start = CHECKIN.matcher(text); var confirm = CONFIRM.matcher(text); var took = TOOK.matcher(text);
            if (start.matches()) {
                int id = positive(start.group(1));
                if (id > 999999999) throw new IllegalArgumentException();
                expected = new JourneyDecision.Start(id);
            } else if (confirm.matches()) {
                expected = new JourneyDecision.Confirm(positive(confirm.group(2)),positive(confirm.group(1)),
                        ConfirmacaoStatus.valueOf(confirm.group(3).toUpperCase(Locale.ROOT)));
            } else if (took.matches()) {
                expected = new JourneyDecision.Confirm(positive(took.group(1)),positive(took.group(2)),ConfirmacaoStatus.CONFIRMADO);
            } else {
                String reply = checkin ? CHECKIN_FORMAT : text.contains("meu") ? IDENTIFY : CLARIFY;
                return Optional.of(new JourneyDecision.Reply(reply));
            }
        } catch (IllegalArgumentException e) { return Optional.of(new JourneyDecision.Reply(checkin ? CHECKIN_FORMAT : CLARIFY)); }
        if (gemini.isEmpty()) return Optional.of(new JourneyDecision.Reply(checkin ? CHECKIN_FORMAT : CLARIFY));
        try {
            var json = GeminiOutputValidator.object(gemini.get().interpretJourney(input),
                    Set.of("intent","idBeneficiario","idMedicacao","statusConfirmacao"));
            if (!json.get("intent").isTextual()) throw new IllegalArgumentException();
            JourneyIntent intent = JourneyIntent.valueOf(json.get("intent").textValue());
            JourneyDecision actual = switch (intent) {
                case REALIZAR_CHECKIN -> {
                    if (!json.get("idMedicacao").isNull() || !json.get("statusConfirmacao").isNull()) throw new IllegalArgumentException();
                    yield new JourneyDecision.Start(id(json.get("idBeneficiario")));
                }
                case CONFIRMAR_MEDICACAO -> {
                    if (!json.get("statusConfirmacao").isTextual()) throw new IllegalArgumentException();
                    yield new JourneyDecision.Confirm(id(json.get("idBeneficiario")),id(json.get("idMedicacao")),
                            ConfirmacaoStatus.valueOf(json.get("statusConfirmacao").textValue()));
                }
                case FORA_DE_ESCOPO -> new JourneyDecision.Reply(NaturalLanguageAssistant.OUT_OF_SCOPE);
            };
            if (!expected.equals(actual)) throw new IllegalArgumentException();
            return Optional.of(actual);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { /* Nunca publicar a saída arbitrária do modelo. */ }
        return Optional.of(new JourneyDecision.Reply(NaturalLanguageAssistant.FALLBACK));
    }
    private static int positive(String text) { int id = Integer.parseInt(text); if (id < 1) throw new IllegalArgumentException(); return id; }
    private static int id(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 1) throw new IllegalArgumentException();
        return node.intValue();
    }
}
