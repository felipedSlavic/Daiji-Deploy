package br.fiap.daiji.assistant;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ResponseFormatter {
    public String format(AssistantResponse response) {
        return switch (response) {
            case AssistantResponse.Text r -> r.text();
            case AssistantResponse.ScoreExplanation r -> "Score atual: "
                    + r.score().valor().stripTrailingZeros().toPlainString() + "/100 — " + r.score().classificacao()
                    + ".\n\n" + r.paragraph() + "\n\n" + MessageRouter.AVISO;
            case AssistantResponse.Score r -> "🩺 Score Daiji\n\n" + value(r.nome()) + "\nScore atual: "
                    + r.valor().stripTrailingZeros().toPlainString() + "/100\nClassificação: " + value(r.classificacao())
                    + "\n\n" + MessageRouter.AVISO;
            case AssistantResponse.Medicacoes r -> r.itens().isEmpty() ? "Nenhuma medicação cadastrada."
                    : "💊 Medicações cadastradas\n\n" + r.itens().stream().map(m -> "• " + value(m.nome())
                    + " — " + value(m.dosagem()) + " — " + value(m.horario())).collect(Collectors.joining("\n"));
            case AssistantResponse.MedicacoesComIds r -> r.itens().isEmpty() ? "Nenhuma medicação cadastrada."
                    : "💊 Medicações cadastradas (IDs)\n\n" + r.itens().stream().map(m -> "• ID " + m.idMedicacao()
                    + " — " + value(m.nomeMedicamento()) + " — " + value(m.dosagem()) + " — " + value(m.horarioPrevisto()))
                    .collect(Collectors.joining("\n"));
            case AssistantResponse.Checkins r -> r.itens().isEmpty() ? "Nenhum check-in registrado."
                    : "📋 Check-ins recentes\n\n" + r.itens().stream().map(c ->
                    (c.data() == null ? "Não informado" : c.data().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                    + "\nEstresse: " + (c.estresse() == null ? "Não informado" : c.estresse() + "/5")
                    + "\nSono: " + value(c.sono()) + "\nAlimentação: " + value(c.alimentacao())
                    + "\nHumor: " + value(c.humor())).collect(Collectors.joining("\n\n"));
        };
    }
    private String value(String text) { return text == null || text.isBlank() ? "Não informado" : text; }
}
