package br.fiap.daiji.assistant;

import br.fiap.daiji.service.CheckinService;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

@Component
public class CheckinsCommandHandler implements CommandHandler {
    private final CheckinService service;
    public CheckinsCommandHandler(CheckinService service) { this.service = service; }
    public String command() { return "/checkins"; }
    public AssistantResponse handle(int id) throws SQLException {
        return new AssistantResponse.Checkins(service.recentes(id));
    }
}
