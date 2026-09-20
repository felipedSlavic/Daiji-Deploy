package br.fiap.daiji.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpTelegramClientTest {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
    ObjectMapper mapper = new ObjectMapper();
    // Transporte HTTP inteiramente simulado; nenhum token real ou inventado é necessário.
    HttpTelegramClient client = new HttpTelegramClient("", http, mapper);
    @BeforeEach void prepare() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true,\"result\":{}}");
    }
    @Test void getUpdatesParsingEContrato() throws Exception {
        when(response.body()).thenReturn("""
                {"ok":true,"result":[{"update_id":42,"message":{"text":"Olá 🩺","chat":{"id":9000000000,"type":"private"},"extra":1}},
                {"update_id":43,"callback_query":{}},{"update_id":44,"message":{"chat":{"id":1,"type":"private"}}}]}
                """);
        var updates = client.getUpdates(40,25);
        assertEquals(3, updates.size()); assertEquals(42L, updates.getFirst().updateId());
        assertEquals("Olá 🩺", updates.getFirst().message().text());
        assertEquals(9000000000L, updates.getFirst().message().chat().id());
        assertNull(updates.get(1).message()); assertNull(updates.get(2).message().text());
        var request = capture().getFirst(); var json = mapper.readTree(body(request));
        assertEquals(40, json.get("offset").asLong()); assertEquals(25, json.get("timeout").asInt());
        assertEquals("message", json.get("allowed_updates").get(0).asText());
        assertEquals(35, request.timeout().orElseThrow().toSeconds());
        assertEquals("POST", request.method()); assertTrue(request.uri().getPath().endsWith("/getUpdates"));
    }
    @Test void updateMalformadoNaoPerdeId() throws Exception {
        when(response.body()).thenReturn("{\"ok\":true,\"result\":[{\"update_id\":1,\"message\":false},{\"message\":{}},{\"update_id\":2}]}");
        var updates = client.getUpdates(0,25);
        assertEquals(List.of(1L,2L), updates.stream().map(TelegramUpdate::updateId).toList());
        assertNull(updates.getFirst().message());
    }
    @Test void envioTextoPlanoUnicode() throws Exception {
        String text = "Olá 🩺 \"teste\"\n<>&_* \\";
        client.sendMessage(9000000000L,text);
        var request = capture().getFirst(); var json = mapper.readTree(body(request));
        assertEquals(text,json.get("text").asText()); assertEquals(9000000000L,json.get("chat_id").asLong());
        assertFalse(json.has("parse_mode")); assertTrue(request.uri().getPath().endsWith("/sendMessage"));
    }
    @Test void mensagensGrandesNaoCortamEmoji() throws Exception {
        String text = "a".repeat(3999) + "🩺" + "b".repeat(4100);
        client.sendMessage(1,text);
        var requests = capture(); assertEquals(3,requests.size()); StringBuilder joined = new StringBuilder();
        for (var request : requests) {
            String part = mapper.readTree(body(request)).get("text").asText();
            assertTrue(part.length() <= 4000); joined.append(part);
        }
        assertEquals(text,joined.toString());
    }
    @ParameterizedTest @ValueSource(ints = {400,401,404,409,429,500,503})
    void erroHttpSanitizado(int status) throws Exception {
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("{\"ok\":false,\"description\":\"conteudo-sensivel\",\"parameters\":{\"retry_after\":7}}");
        var error = assertThrows(TelegramApiException.class, () -> client.getUpdates(0,25));
        assertEquals("Falha na comunicação com Telegram.", error.getMessage()); assertNull(error.getCause());
        assertEquals(status == 401 || status == 404,error.invalidCredential());
        if (status == 429) assertEquals(7,error.retryAfter());
    }
    @ParameterizedTest @ValueSource(strings = {"html-sensivel", "null", "{}", "{\"ok\":true}", "{\"ok\":true,\"result\":{}}", "{\"ok\":false,\"description\":\"interno\"}"})
    void jsonInvalidoSeguro(String body) {
        when(response.body()).thenReturn(body);
        var error = assertThrows(TelegramApiException.class, () -> client.getUpdates(0,25));
        assertNull(error.getCause()); assertEquals("Falha na comunicação com Telegram.",error.getMessage());
    }
    @Test void excecaoTransporteSemCausaOuDetalhe() throws Exception {
        when(http.send(any(),any())).thenThrow(new IOException("https://servidor/segredo"));
        var error = assertThrows(TelegramApiException.class, () -> client.sendMessage(1,"Oi"));
        assertNull(error.getCause()); assertFalse(error.toString().contains("segredo"));
    }
    @Test void interrupcaoPreservadaSanitizada() throws Exception {
        when(http.send(any(),any())).thenThrow(new InterruptedException("segredo"));
        try {
            var error = assertThrows(InterruptedException.class, () -> client.getUpdates(0,25));
            assertTrue(Thread.currentThread().isInterrupted()); assertFalse(error.toString().contains("segredo"));
        } finally { Thread.interrupted(); }
    }
    List<HttpRequest> capture() throws Exception {
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, atLeastOnce()).send(captor.capture(),any()); return captor.getAllValues();
    }
    static String body(HttpRequest request) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part); }
            public void onError(Throwable t) { throw new AssertionError(t); }
            public void onComplete() {}
        });
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
