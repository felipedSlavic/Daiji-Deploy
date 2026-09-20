package br.fiap.daiji.assistant;

import br.fiap.daiji.service.BeneficiarioService;
import br.fiap.daiji.service.ScoreService;
import java.sql.SQLException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ScoreCommandHandler implements CommandHandler {
    private final BeneficiarioService beneficiarios;
    private final ScoreService scores;
    public ScoreCommandHandler(BeneficiarioService beneficiarios, ScoreService scores) {
        this.beneficiarios = beneficiarios;
        this.scores = scores;
    }
    public String command() { return "/score"; }
    public AssistantResponse handle(int id) throws SQLException {
        var pessoa = beneficiarios.buscarPorId(id);
        if (pessoa.isEmpty()) return new AssistantResponse.Text("Beneficiário não encontrado.");
        try {
            var score = scores.atual(id);
            return new AssistantResponse.Score(pessoa.get().getNome(), score.valorScore(), score.classificacaoRisco());
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 404) throw e;
            // Distingue ausência de score da exclusão concorrente do beneficiário.
            return new AssistantResponse.Text(beneficiarios.buscarPorId(id).isEmpty()
                    ? "Beneficiário não encontrado." : "Ainda não há score calculado para este beneficiário.");
        }
    }
}
