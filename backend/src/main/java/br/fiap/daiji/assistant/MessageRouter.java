package br.fiap.daiji.assistant;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MessageRouter {
    public static final String AVISO = "O Score Daiji é demonstrativo e não substitui avaliação profissional.";
    public static final String AJUDA = """
            /score <id>
            Consulta o Score Daiji mais recente.

            /medicacoes <id>
            Lista medicações cadastradas.

            /checkins <id>
            Mostra os 3 check-ins mais recentes.

            /ajuda (ou /help)
            Mostra os comandos.

            """ + AVISO;
    public static final String ERRO = "Não foi possível consultar essa informação agora. Tente novamente em instantes.";
    private final Map<String, CommandHandler> handlers;
    private final NaturalLanguageResponder naturalLanguage;
    public MessageRouter(List<CommandHandler> handlers) {
        this(handlers, text -> legacyFallback());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public MessageRouter(List<CommandHandler> handlers, NaturalLanguageResponder naturalLanguage) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(CommandHandler::command, Function.identity()));
        this.naturalLanguage = naturalLanguage;
    }
    public AssistantResponse route(String text) {
        String[] partes = (text == null ? "" : text.strip()).split("\\s+");
        String comando = partes[0].split("@", 2)[0].toLowerCase(Locale.ROOT);
        // Aceita o escape Markdown quando o comando é colado como texto literal.
        if (comando.equals("/medicacoes\\_ids")) comando = "/medicacoes_ids";
        if (comando.equals("/start")) return new AssistantResponse.Text("""
                Olá! Eu sou o assistente Daiji.

                Posso consultar informações do acompanhamento do MVP.

                Comandos disponíveis:
                /score <id>
                /medicacoes <id>
                /checkins <id>
                /ajuda

                Exemplo:
                /score 1""");
        if (comando.equals("/ajuda") || comando.equals("/help")) return new AssistantResponse.Text(AJUDA);
        var handler = handlers.get(comando);
        if (handler == null) return naturalText(text);
        int id;
        try {
            if (partes.length != 2 || !partes[1].matches("[0-9]+")) throw new NumberFormatException();
            id = Integer.parseInt(partes[1]);
            if (id < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            return new AssistantResponse.Text("Formato inválido.\nUse: " + comando + " <idBeneficiario>\nExemplo: " + comando + " 1");
        }
        try { return handler.handle(id); }
        catch (ResponseStatusException e) {
            return new AssistantResponse.Text(e.getStatusCode().value() == 404 ? "Beneficiário não encontrado." : ERRO);
        } catch (Exception e) { return new AssistantResponse.Text(ERRO); }
    }
    private AssistantResponse naturalText(String text) {
        if (text != null && text.strip().startsWith("/")) return legacyFallback();
        try { return naturalLanguage.answer(text); }
        catch (Exception e) { return new AssistantResponse.Text(NaturalLanguageAssistant.FALLBACK); }
    }
    private static AssistantResponse legacyFallback() {
        return new AssistantResponse.Text("Não entendi essa mensagem ainda.\n\nUse /ajuda para ver os comandos disponíveis.\n\n"
                + "Em breve o assistente Daiji também poderá responder perguntas em linguagem natural.");
    }
}
