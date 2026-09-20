package br.fiap.daiji.assistant;

import br.fiap.daiji.gemini.GeminiClient;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NaturalLanguageAssistant implements NaturalLanguageResponder {
    public static final String FALLBACK = "Não consegui interpretar essa pergunta agora.\nVocê ainda pode usar /ajuda para consultar os comandos disponíveis.";
    public static final String ASK_ID = "Informe o ID do beneficiário.\nExemplo: Como está o score do beneficiário 1?";
    public static final String INVALID_ID = "Informe um único ID de beneficiário válido, inteiro e positivo.\nExemplo: Como está o score do beneficiário 1?";
    public static final String OUT_OF_SCOPE = "Não posso diagnosticar, prescrever, alterar tratamentos ou fornecer dados privados e credenciais. "
            + "O Daiji é um MVP de acompanhamento e seu Score é demonstrativo. Para orientação médica, procure um profissional de saúde. "
            + "Em situações urgentes, procure atendimento de emergência.";
    public static final String HELP = "Olá! Sou o assistente Daiji. Posso consultar e explicar o score, listar medicações cadastradas e mostrar check-ins recentes.\n\n"
            + "Informe o ID, por exemplo: Explique o score do beneficiário 1.\nOs comandos estão em /ajuda.\n\n" + MessageRouter.AVISO;
    public static final String EXPLANATION_FAILED = "Não foi possível gerar a explicação detalhada agora.";
    private final Optional<GeminiClient> client;
    private final Map<String, CommandHandler> handlers;
    public NaturalLanguageAssistant(Optional<GeminiClient> client, List<CommandHandler> handlers) {
        this.client = client;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(CommandHandler::command, Function.identity()));
    }
    @Override public AssistantResponse answer(String text) {
        if (AssistantSafety.blocked(text)) return new AssistantResponse.Text(OUT_OF_SCOPE);
        String normalized = AssistantSafety.normalize(text).replaceAll("[!?.,]+$", "");
        if (Set.of("oi", "ola", "bom dia", "boa tarde", "boa noite", "o que voce faz", "como voce pode me ajudar",
                "oi, o que voce consegue fazer").contains(normalized)) return new AssistantResponse.Text(HELP);
        Integer explicitId;
        try { explicitId = AssistantSafety.explicitId(text); }
        catch (IllegalArgumentException e) { return new AssistantResponse.Text(INVALID_ID); }
        if (explicitId == null && normalized.matches("(?s).*\\b(score|medicacoes|medicamentos|check-?ins)\\b.*"))
            return new AssistantResponse.Text(ASK_ID);
        if (client.isEmpty()) return new AssistantResponse.Text(FALLBACK);
        GeminiOutputValidator.Interpretation interpretation;
        try {
            interpretation = GeminiOutputValidator.interpretation(client.get().interpret(text));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return new AssistantResponse.Text(FALLBACK);
        } catch (Exception e) { return new AssistantResponse.Text(FALLBACK); }
        if (interpretation.intent().requiresId()) {
            if (explicitId == null) return new AssistantResponse.Text(ASK_ID);
            if (!explicitId.equals(interpretation.idBeneficiario())) return new AssistantResponse.Text(FALLBACK);
        }
        try {
            return switch (interpretation.intent()) {
                case AJUDA, SAUDACAO -> new AssistantResponse.Text(HELP);
                case FORA_DE_ESCOPO -> new AssistantResponse.Text(OUT_OF_SCOPE);
                case CONSULTAR_SCORE -> handlers.get("/score").handle(explicitId);
                case CONSULTAR_MEDICACOES -> handlers.get("/medicacoes").handle(explicitId);
                case CONSULTAR_CHECKINS -> handlers.get("/checkins").handle(explicitId);
                case EXPLICAR_SCORE -> explain(explicitId);
            };
        } catch (ResponseStatusException e) {
            return new AssistantResponse.Text(e.getStatusCode().value() == 404 ? "Beneficiário não encontrado." : MessageRouter.ERRO);
        } catch (Exception e) { return new AssistantResponse.Text(MessageRouter.ERRO); }
    }
    private AssistantResponse explain(int id) throws java.sql.SQLException {
        var result = handlers.get("/score").handle(id);
        if (!(result instanceof AssistantResponse.Score score)) return result;
        String paragraph;
        try { paragraph = GeminiOutputValidator.explanation(client.orElseThrow().explain(ScoreFacts.from(score))); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); paragraph = EXPLANATION_FAILED; }
        catch (Exception e) { paragraph = EXPLANATION_FAILED; }
        return new AssistantResponse.ScoreExplanation(score, paragraph);
    }
}
