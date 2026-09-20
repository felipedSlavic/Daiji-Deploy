package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"telegram.bot.enabled=false", "gemini.enabled=false",
        "CORS_ALLOWED_ORIGINS=https://app.example.test", "PORT=18081"})
@AutoConfigureMockMvc
class ConfiguredCorsApiTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;
    @MockitoBean ConnectionFactory factory;

    @Test void origemConfiguradaPermitePatch() throws Exception {
        mvc.perform(options("/api/gestao/encaminhamentos/1").header("Origin", "https://app.example.test")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"));
        verifyNoInteractions(factory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5500", "http://127.0.0.1:5500",
            "http://localhost:5501", "http://127.0.0.1:5501", "https://app.example.test.nao-autorizado.example"})
    void configuracaoSubstituiOrigensLocaisSemAceitarOutroHost(String origin) throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(factory);
    }

    @Test void portaUsaVariavelDaHospedagem() {
        assertEquals("18081", environment.getProperty("server.port"));
    }
}
