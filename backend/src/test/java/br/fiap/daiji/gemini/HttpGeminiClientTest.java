package br.fiap.daiji.gemini;

import br.fiap.daiji.assistant.ScoreFacts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpGeminiClientTest {
    HttpClient http = mock(HttpClient.class);
    @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
    ObjectMapper json = new ObjectMapper();
    MutableClock clock = new MutableClock();
    // Chave vazia, HTTP mockado: nenhum token real ou inventado nos testes.
    HttpGeminiClient client = new HttpGeminiClient(http,json,"","modelo-configuravel",Duration.ofSeconds(12),clock);
    @BeforeEach void prepare() throws Exception {
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b) -> true));
        success("{\"intent\":\"AJUDA\",\"idBeneficiario\":null}");
    }
    void success(String output) throws Exception {
        when(response.body()).thenReturn(json.writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP",
                "content",Map.of("parts",List.of(Map.of("text",output))))))));
    }
    HttpRequest request() throws Exception {
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(),any()); return captor.getValue();
    }
    @Test void contratoInterpretacaoModeloHeaderSchemaSemFerramentas() throws Exception {
        String text = "Qual é o score do beneficiário 1?";
        assertTrue(client.interpret(text).contains("AJUDA"));
        var req = request(); var body = json.readTree(body(req));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/modelo-configuravel:generateContent",req.uri().toString());
        assertNull(req.uri().getQuery()); assertTrue(req.headers().firstValue("x-goog-api-key").isPresent());
        assertEquals(12,req.timeout().orElseThrow().toSeconds());
        assertEquals("POST",req.method()); assertEquals(text,body.path("contents").get(0).path("parts").get(0).path("text").asText());
        assertEquals(1,body.path("contents").size()); assertFalse(body.has("tools")); assertFalse(body.has("cachedContent"));
        assertTrue(body.path("systemInstruction").toString().contains("não confiável"));
        var config = body.path("generationConfig"); assertEquals("application/json",config.path("responseMimeType").asText());
        assertEquals(7,config.path("responseJsonSchema").path("properties").path("intent").path("enum").size());
        assertFalse(config.path("responseJsonSchema").path("additionalProperties").asBoolean(true));
        assertFalse(body.toString().contains("chatId"));
    }
    @Test void explicacaoEnviaSomenteFatosMinimos() throws Exception {
        client.explain(new ScoreFacts(new BigDecimal("97.00"),"BAIXO",false));
        var body = json.readTree(body(request()));
        var facts = json.readTree(body.path("contents").get(0).path("parts").get(0).path("text").asText());
        assertEquals(3,facts.size()); assertEquals(97,facts.path("valorScore").asInt());
        assertEquals("BAIXO",facts.path("classificacaoRisco").asText()); assertFalse(facts.path("fatoresHistoricosDisponiveis").asBoolean());
        for (String field : List.of("nome","cpf","email","telefone","idBeneficiario","chatId","dataNascimento","medicacoes","respostaTexto"))
            assertFalse(facts.has(field));
        assertFalse(body.has("tools")); assertTrue(body.path("systemInstruction").toString().contains("não recalcule"));
    }
    @ParameterizedTest @ValueSource(ints = {400,401,403,404,429,500,503})
    void errosHttpSemCorpoSemChaveSemCausa(int code) throws Exception {
        when(response.statusCode()).thenReturn(code); when(response.body()).thenReturn("detalhe-remoto-restrito");
        var error = assertThrows(GeminiException.class,() -> client.interpret("Oi"));
        assertEquals("Gemini temporariamente indisponível.",error.getMessage()); assertNull(error.getCause());
        assertFalse(error.toString().contains("restrito")); verify(http).send(any(),any());
    }
    @ParameterizedTest @ValueSource(ints = {401,403})
    void credencialRecusadaSuspendeAteReinicio(int code) throws Exception {
        when(response.statusCode()).thenReturn(code);
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); clock.advance(86401);
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); verify(http).send(any(),any());
    }
    @Test void rateLimitHeaderRespeitaCooldownSemSleepOuRetry() throws Exception {
        when(response.statusCode()).thenReturn(429);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("60")),(a,b) -> true));
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); clock.advance(59);
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); verify(http).send(any(),any());
        clock.advance(1); when(response.statusCode()).thenReturn(200); assertNotNull(client.interpret("Oi"));
        verify(http,times(2)).send(any(),any());
    }
    @Test void rateLimitRetryInfoFracionario() throws Exception {
        when(response.statusCode()).thenReturn(429);
        when(response.body()).thenReturn("{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"45.5s\"}]}}");
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); clock.advance(45);
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); verify(http).send(any(),any());
        clock.advance(1); when(response.statusCode()).thenReturn(200); success("{}"); assertEquals("{}",client.interpret("Oi"));
    }
    @Test void retryAfterDataHttp() throws Exception {
        when(response.statusCode()).thenReturn(429);
        String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(clock.instant().plusSeconds(90).atZone(ZoneOffset.UTC));
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of(date)),(a,b) -> true));
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); clock.advance(89);
        assertThrows(GeminiException.class,() -> client.interpret("Oi")); verify(http).send(any(),any());
    }
    @ParameterizedTest @ValueSource(strings = {"timeout","io"})
    void transporteSanitizadoECooldown(String type) throws Exception {
        when(http.send(any(),any())).thenThrow(type.equals("timeout") ? new HttpTimeoutException("detalhe-restrito") : new IOException("detalhe-restrito"));
        var error = assertThrows(GeminiException.class,() -> client.interpret("Oi")); assertNull(error.getCause());
        assertFalse(error.toString().contains("restrito")); assertThrows(GeminiException.class,() -> client.interpret("Oi"));
        verify(http).send(any(),any());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"html", "null", "{}", "{\"candidates\":[]}",
            "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}",
            "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}", "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"\"}]}}]}",
            "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"functionCall\":{}}]}}]}"})
    void envelopeInvalidoBloqueado(String raw) {
        when(response.body()).thenReturn(raw); assertThrows(GeminiException.class,() -> client.interpret("Oi"));
    }
    @Test void interrupcaoSanitizada() throws Exception {
        when(http.send(any(),any())).thenThrow(new InterruptedException("restrito"));
        try {
            var error = assertThrows(InterruptedException.class,() -> client.interpret("Oi"));
            assertTrue(Thread.currentThread().isInterrupted()); assertFalse(error.toString().contains("restrito"));
        } finally { Thread.interrupted(); }
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
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-17T12:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
