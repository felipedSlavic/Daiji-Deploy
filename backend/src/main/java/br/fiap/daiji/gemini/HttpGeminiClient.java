package br.fiap.daiji.gemini;

import br.fiap.daiji.assistant.ScoreFacts;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HttpGeminiClient implements GeminiClient {
    private final HttpClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final Clock clock;
    private volatile Instant retryAt = Instant.MIN;
    private volatile boolean credentialsRejected;

    public HttpGeminiClient(HttpClient http, ObjectMapper json, String apiKey, String model, Duration timeout, Clock clock) {
        this.http = http;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.clock = clock;
    }
    public String interpret(String text) throws InterruptedException {
        return generate(GeminiProtocol.INTERPRET, text, GeminiProtocol.intentSchema());
    }
    public String interpretJourney(String text) throws InterruptedException {
        return generate(GeminiProtocol.JOURNEY, text, GeminiProtocol.journeySchema());
    }
    public String explain(ScoreFacts facts) throws InterruptedException {
        try { return generate(GeminiProtocol.EXPLAIN, json.writeValueAsString(facts), GeminiProtocol.explanationSchema()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new GeminiException(); }
    }
    private synchronized String generate(String instruction, String data, Map<String, Object> schema) throws InterruptedException {
        if (credentialsRejected || clock.instant().isBefore(retryAt)) throw new GeminiException();
        try {
            var body = Map.of("systemInstruction", Map.of("parts", List.of(Map.of("text", instruction))),
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", data)))),
                    "generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", schema,
                            "candidateCount", 1, "maxOutputTokens", 1024));
            var request = HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent")).timeout(timeout)
                    .header("Content-Type", "application/json").header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 401 || status == 403) credentialsRejected = true;
            if (status != 200) {
                // Sem sleep no worker Telegram: cooldown limita novas chamadas e responde fallback imediatamente.
                long seconds = status == 429 ? retrySeconds(response) : 5;
                retryAt = clock.instant().plusSeconds(seconds);
                throw new GeminiException();
            }
            String raw = response.body();
            if (raw == null || raw.length() > 65536) throw new GeminiException();
            JsonNode root = json.readTree(raw);
            if (root == null || root.path("promptFeedback").has("blockReason")) throw new GeminiException();
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.size() != 1) throw new GeminiException();
            JsonNode candidate = candidates.get(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) throw new GeminiException();
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray()) throw new GeminiException();
            StringBuilder result = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) continue;
                if (!part.path("text").isTextual() || part.has("functionCall")) throw new GeminiException();
                result.append(part.get("text").textValue());
            }
            if (result.isEmpty() || result.toString().isBlank() || result.length() > 6000) throw new GeminiException();
            return result.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new InterruptedException("Comunicação Gemini interrompida.");
        } catch (GeminiException e) { throw e; }
        catch (Exception e) {
            retryAt = clock.instant().plusSeconds(5);
            throw new GeminiException();
        }
    }
    private long retrySeconds(HttpResponse<String> response) {
        long seconds = 30;
        String header = response.headers().firstValue("Retry-After").orElse("");
        try { seconds = Math.max(seconds, Long.parseLong(header)); }
        catch (Exception ignored) {
            try { seconds = Math.max(seconds, Duration.between(clock.instant(),
                    ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); }
            catch (Exception ignoredDate) { /* Ausência: cooldown padrão. */ }
        }
        try {
            String raw = response.body();
            if (raw != null && raw.length() <= 65536) {
                JsonNode details = json.readTree(raw).path("error").path("details");
                for (JsonNode detail : details) {
                    if ("type.googleapis.com/google.rpc.RetryInfo".equals(detail.path("@type").asText())) {
                        String delay = detail.path("retryDelay").asText();
                        if (delay.matches("[0-9]+(?:\\.[0-9]+)?s"))
                            seconds = Math.max(seconds, (long) Math.ceil(Double.parseDouble(delay.substring(0, delay.length() - 1))));
                    }
                }
            }
        } catch (Exception ignored) { /* Nunca propagar corpo de erro. */ }
        // Cooldown defensivamente limitado a 24 horas, sem fila ou bloqueio do worker.
        return Math.min(seconds, 86400);
    }
}
