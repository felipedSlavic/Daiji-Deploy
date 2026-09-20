package br.fiap.daiji.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JourneyProtocolTest {
    @Test @SuppressWarnings("unchecked")
    void novoContratoHttpFechadoPreservaContratoCP08() throws Exception {
        HttpClient http = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}");
        var client = new HttpGeminiClient(http,new ObjectMapper(),"","modelo-configuravel",Duration.ofSeconds(12),Clock.systemUTC());
        String text = "Quero fazer o check-in do ID 1"; client.interpretJourney(text);
        var captor = ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(captor.capture(),any());
        var body = new ObjectMapper().readTree(HttpGeminiClientTest.body(captor.getValue()));
        assertEquals(text,body.path("contents").get(0).path("parts").get(0).path("text").asText());
        var schema = body.path("generationConfig").path("responseJsonSchema");
        assertEquals(4,schema.path("required").size()); assertEquals(3,schema.path("properties").path("intent").path("enum").size());
        assertFalse(schema.path("additionalProperties").asBoolean(true)); assertFalse(body.has("tools"));
        assertEquals(7,new ObjectMapper().valueToTree(GeminiProtocol.intentSchema()).path("properties").path("intent").path("enum").size());
    }
}
