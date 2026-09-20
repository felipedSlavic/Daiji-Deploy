package br.fiap.daiji.assistant;

import br.fiap.daiji.dto.CheckinRequest;
import br.fiap.daiji.model.CanalCheckin;
import br.fiap.daiji.service.CheckinService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Estado efêmero, sem dependência do polling ou Gemini. Operações serializadas por conversa. */
@Component
public class CheckinConversations implements AutoCloseable {
    public static final Duration TTL = Duration.ofMinutes(15);
    public static final String START = "Vamos realizar o check-in.\n\nDe 1 a 5, qual é seu nível de estresse hoje?";
    public static final String SLEEP = "Como foi a qualidade do seu sono?\nSugestões: RUIM, REGULAR ou BOA. Pode informar outro texto de até 20 bytes UTF-8.";
    public static final String FOOD = "Como foi a qualidade da sua alimentação?\nSugestões: RUIM, REGULAR ou BOA. Pode informar outro texto de até 30 bytes UTF-8.";
    public static final String MOOD = "Como está seu humor?\nSugestões: CALMO, REGULAR ou TRISTE. Pode informar outro texto de até 20 bytes UTF-8.";
    public static final String NOTE = "Deseja adicionar uma observação sobre o dia?\nEnvie o texto ou digite PULAR.";
    public static final String ACTIVE = "Existe um check-in em andamento. Responda à etapa atual ou use /cancelar.";
    public static final String EXPIRED = "O check-in expirou após 15 minutos de inatividade. Inicie novamente com /checkin <idBeneficiario>.";
    public static final String FAILED = "Não foi possível confirmar o registro agora. O fluxo foi encerrado. Consulte /checkins <idBeneficiario> antes de iniciar outro check-in.";
    private enum Stage { STRESS, SLEEP, FOOD, MOOD, NOTE, CONFIRM }
    private static class State {
        final int id;
        Stage stage = Stage.STRESS;
        CheckinRequest data = new CheckinRequest(null,null,null,null,null);
        Instant lastInteraction;
        State(int id, Instant now) { this.id = id; this.lastInteraction = now; }
    }
    private final ConcurrentHashMap<Long,State> states = new ConcurrentHashMap<>();
    private final CheckinService service;
    private final Clock clock;
    private ScheduledExecutorService cleaner;
    @Autowired public CheckinConversations(CheckinService service) { this(service,Clock.systemUTC()); }
    public CheckinConversations(CheckinService service, Clock clock) { this.service = service; this.clock = clock; }

    @PostConstruct public void startCleanup() {
        cleaner = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("daiji-checkin-expiry").factory());
        cleaner.scheduleWithFixedDelay(this::expire, 1, 1, TimeUnit.MINUTES);
    }
    public AssistantResponse start(long conversationId, int beneficiario) {
        String[] reply = {START};
        states.compute(conversationId, (key, state) -> {
            if (state != null && !expired(state)) { reply[0] = ACTIVE; return state; }
            try {
                service.validarBeneficiario(beneficiario);
                return new State(beneficiario,clock.instant());
            } catch (ResponseStatusException e) {
                reply[0] = e.getStatusCode().value() == 404 ? "Beneficiário não encontrado." : "Informe um ID de beneficiário válido.";
            } catch (Exception e) { reply[0] = MessageRouter.ERRO; }
            return null;
        });
        return new AssistantResponse.Text(reply[0]);
    }
    /** Vazio significa ausência de fluxo: o router pode tratar a mensagem normalmente. */
    public Optional<AssistantResponse> handle(long conversationId, String input) {
        String text = input == null ? "" : input.strip();
        String[] reply = {null};
        states.compute(conversationId, (key, state) -> {
            boolean cancel = text.equalsIgnoreCase("CANCELAR") || text.matches("(?i)/cancelar(?:@[A-Za-z0-9_]+)?");
            if (state == null) {
                if (text.toLowerCase(Locale.ROOT).startsWith("/cancelar")) reply[0] = "Não há check-in em andamento.";
                return null;
            }
            if (expired(state)) { reply[0] = EXPIRED; return null; }
            if (cancel) { reply[0] = "Check-in cancelado."; return null; }
            state.lastInteraction = clock.instant();
            if (text.startsWith("/")) { reply[0] = ACTIVE; return state; }
            var data = state.data;
            switch (state.stage) {
                case STRESS -> {
                    if (!text.matches("[1-5]")) { reply[0] = "Informe um número de 1 a 5."; break; }
                    state.data = new CheckinRequest(Integer.parseInt(text),null,null,null,null);
                    state.stage = Stage.SLEEP; reply[0] = SLEEP;
                }
                case SLEEP, FOOD, MOOD -> {
                    String value = text.toUpperCase(Locale.ROOT);
                    var next = switch (state.stage) {
                        case SLEEP -> new CheckinRequest(data.nivelEstresse(),value,null,null,null);
                        case FOOD -> new CheckinRequest(data.nivelEstresse(),data.qualidadeSono(),value,null,null);
                        default -> new CheckinRequest(data.nivelEstresse(),data.qualidadeSono(),data.qualidadeAlimentacao(),value,null);
                    };
                    try {
                        if (value.isBlank()) throw new IllegalArgumentException();
                        CheckinService.validarRequest(next);
                        state.data = next;
                        state.stage = switch (state.stage) { case SLEEP -> Stage.FOOD; case FOOD -> Stage.MOOD; default -> Stage.NOTE; };
                        reply[0] = switch (state.stage) { case FOOD -> FOOD; case MOOD -> MOOD; default -> NOTE; };
                    } catch (RuntimeException e) {
                        reply[0] = "Resposta inválida. " + switch (state.stage) { case SLEEP -> SLEEP; case FOOD -> FOOD; default -> MOOD; };
                    }
                }
                case NOTE -> {
                    String value = text.equalsIgnoreCase("PULAR") ? null : text;
                    var next = new CheckinRequest(data.nivelEstresse(),data.qualidadeSono(),data.qualidadeAlimentacao(),data.humor(),value);
                    try {
                        if (text.isBlank()) throw new IllegalArgumentException();
                        CheckinService.validarRequest(next);
                        state.data = next; state.stage = Stage.CONFIRM;
                        reply[0] = summary(next);
                    } catch (RuntimeException e) { reply[0] = "Envie uma observação de até 500 bytes UTF-8 ou digite PULAR."; }
                }
                case CONFIRM -> {
                    if (!text.equalsIgnoreCase("CONFIRMAR")) { reply[0] = "Digite CONFIRMAR para registrar ou CANCELAR para descartar."; break; }
                    try {
                        service.criar(state.id, state.data, CanalCheckin.TELEGRAM);
                        reply[0] = "Check-in registrado com sucesso. ✅";
                    } catch (Exception e) { reply[0] = FAILED; }
                    // Inclusive em resultado incerto do commit: não repetir escrita automaticamente.
                    return null;
                }
            }
            return state;
        });
        return Optional.ofNullable(reply[0]).map(AssistantResponse.Text::new);
    }
    private String summary(CheckinRequest r) {
        return "Confira seu check-in:\n\nEstresse: " + r.nivelEstresse() + "/5\nSono: " + r.qualidadeSono()
                + "\nAlimentação: " + r.qualidadeAlimentacao() + "\nHumor: " + r.humor()
                + "\nObservação: " + (r.respostaTexto() == null ? "Não informada" : r.respostaTexto())
                + "\n\nDigite CONFIRMAR para registrar\nou CANCELAR para descartar.";
    }
    private boolean expired(State state) { return !clock.instant().isBefore(state.lastInteraction.plus(TTL)); }
    public void expire() { states.forEach((key, value) -> states.computeIfPresent(key, (id, state) -> expired(state) ? null : state)); }
    int size() { return states.size(); }
    @Override @PreDestroy public void close() { if (cleaner != null) cleaner.shutdownNow(); states.clear(); }
}
