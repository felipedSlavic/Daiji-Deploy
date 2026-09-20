package br.fiap.daiji.assistant;

import br.fiap.daiji.service.MedicacaoService;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

@Component
public class MedicacoesCommandHandler implements CommandHandler {
    private final MedicacaoService service;
    public MedicacoesCommandHandler(MedicacaoService service) { this.service = service; }
    public String command() { return "/medicacoes"; }
    public AssistantResponse handle(int id) throws SQLException {
        return new AssistantResponse.Medicacoes(service.listar(id).stream().map(m ->
                new AssistantResponse.Medicamento(m.nomeMedicamento(), m.dosagem(), m.horarioPrevisto())).toList());
    }
}
