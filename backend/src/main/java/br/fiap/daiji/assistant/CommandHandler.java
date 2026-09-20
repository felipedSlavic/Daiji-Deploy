package br.fiap.daiji.assistant;

public interface CommandHandler {
    String command();
    AssistantResponse handle(int id) throws java.sql.SQLException;
}
