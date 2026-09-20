package br.fiap.daiji.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;

public record CheckinRequest(
        @JsonDeserialize(using = InteiroEstrito.class) Integer nivelEstresse, String qualidadeSono,
                             String qualidadeAlimentacao, String humor, String respostaTexto) {
    // Impede que o Jackson trunque silenciosamente, por exemplo, 1.5 para 1.
    public static class InteiroEstrito extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }
}
