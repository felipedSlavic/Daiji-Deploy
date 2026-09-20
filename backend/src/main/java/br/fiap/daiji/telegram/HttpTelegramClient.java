package br.fiap.daiji.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class HttpTelegramClient implements TelegramClient, TelegramUpdateSource {
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper;
    public HttpTelegramClient(String token, HttpClient http, ObjectMapper mapper) {
        this.token = token;
        this.http = http;
        this.mapper = mapper;
    }
    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) throws InterruptedException {
        var result = call("getUpdates", Map.of("offset", offset, "timeout", timeoutSeconds,
                "allowed_updates", List.of("message")), Duration.ofSeconds(timeoutSeconds + 10L));
        if (!result.isArray()) throw new TelegramApiException(0, 0);
        List<TelegramUpdate> updates = new ArrayList<>();
        for (JsonNode item : result) {
            JsonNode id = item.get("update_id");
            if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() < 0
                    || id.longValue() == Long.MAX_VALUE) continue;
            try { updates.add(mapper.treeToValue(item, TelegramUpdate.class)); }
            catch (Exception e) {
                // Mantém o ID para confirmar um update malformado sem travar os seguintes.
                updates.add(new TelegramUpdate(id.longValue(), null));
            }
        }
        return updates;
    }
    public void sendMessage(long chatId, String text) throws InterruptedException {
        // Margem conservadora do limite de 4096, sem dividir pares UTF-16.
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + 4000, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            call("sendMessage", Map.of("chat_id", chatId, "text", text.substring(start, end)), Duration.ofSeconds(15));
            start = end;
        }
    }
    private JsonNode call(String method, Map<String, ?> body, Duration timeout) throws InterruptedException {
        try {
            var request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 404)
                throw new TelegramApiException(response.statusCode(), 0);
            JsonNode json = mapper.readTree(response.body());
            if (json == null) throw new TelegramApiException(0, 0);
            if (response.statusCode() != 200 || !json.path("ok").asBoolean(false))
                throw new TelegramApiException(response.statusCode() == 200 ? json.path("error_code").asInt() : response.statusCode(),
                        json.path("parameters").path("retry_after").asInt(0));
            if (!json.hasNonNull("result")) throw new TelegramApiException(0, 0);
            return json.get("result");
        } catch (InterruptedException e) {
            // Nem a mensagem de uma exceção do transporte deve transportar o segredo.
            Thread.currentThread().interrupt();
            throw new InterruptedException("Comunicação Telegram interrompida.");
        } catch (TelegramApiException e) { throw e; }
        catch (Exception e) { throw new TelegramApiException(0, 0); }
    }
}
