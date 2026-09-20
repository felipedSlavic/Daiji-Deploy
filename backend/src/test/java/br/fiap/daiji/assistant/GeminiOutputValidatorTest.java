package br.fiap.daiji.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GeminiOutputValidatorTest {
    @ParameterizedTest @ValueSource(strings = {"{\"intent\":\"CONSULTAR_SCORE\",\"idBeneficiario\":1.5}",
            "{\"intent\":\"CONSULTAR_SCORE\",\"idBeneficiario\":true}", "{\"intent\":\"CONSULTAR_SCORE\",\"idBeneficiario\":2147483648}",
            "{\"intent\":\"CONSULTAR_SCORE\",\"idBeneficiario\":0}", "{\"intent\":\"AJUDA\",\"idBeneficiario\":null}{}",
            "{\"intent\":\"AJUDA\",\"intent\":\"SAUDACAO\",\"idBeneficiario\":null}", "[]", "{\"intent\":\"AJUDA\"}",
            "```json\n{\"intent\":\"AJUDA\",\"idBeneficiario\":null}\n```"})
    void rejeitaTiposExtrasEDuplicatas(String raw) { assertThrows(IllegalArgumentException.class,() -> GeminiOutputValidator.interpretation(raw)); }
    @Test void explicacaoCompostaSomenteDeFrasesPermitidas() {
        String a = GeminiOutputValidator.EXPLANATION_SENTENCES.get(0), b = GeminiOutputValidator.EXPLANATION_SENTENCES.get(2);
        assertEquals(a + " " + b,GeminiOutputValidator.explanation("{\"frases\":[\""+a+"\",\""+b+"\"]}"));
        assertThrows(IllegalArgumentException.class,() -> GeminiOutputValidator.explanation("{\"frases\":[\""+a+"\",\""+a+"\"]}"));
    }
    @ParameterizedTest @ValueSource(strings = {"{\"frases\":[]}","{\"frases\":null}","{\"frases\":[42]}","{\"frases\":\"x\"}","{\"frases\":[],\"score\":95}"})
    void explicacaoInvalida(String raw) { assertThrows(IllegalArgumentException.class,() -> GeminiOutputValidator.explanation(raw)); }
}
