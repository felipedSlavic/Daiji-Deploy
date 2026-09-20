package br.fiap.daiji.assistant;

import br.fiap.daiji.service.MedicacaoService;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

/** Identificadores para o comando de confirmação, preservando a resposta CP07 de /medicacoes. */
@Component
public class MedicacoesIdsCommandHandler implements CommandHandler {
    private final MedicacaoService service;
    public MedicacoesIdsCommandHandler(MedicacaoService service) { this.service = service; }
    public String command() { return "/medicacoes_ids"; }
    public AssistantResponse handle(int id) throws SQLException { return new AssistantResponse.MedicacoesComIds(service.listar(id)); }
}
