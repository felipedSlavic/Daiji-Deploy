package br.fiap.daiji.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.example.test", "", "null", "file:///app",
            "https://app.example.test/caminho", "https://app.example.test?chave=omitida",
            "https://usuario:omitida@app.example.test", "https://app.example.test#fragmento",
            "http://localhost:99999", "https://app.example.test,"})
    void rejeitaConfiguracaoInseguraOuQueNaoRepresentaOrigem(String origins) {
        assertThrows(IllegalArgumentException.class, () -> new CorsConfiguration(origins));
    }
}
