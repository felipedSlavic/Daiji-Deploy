package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"telegram.bot.enabled=false", "gemini.enabled=false"})
@AutoConfigureMockMvc
class CorsApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionFactory factory;

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5500", "http://127.0.0.1:5500",
            "http://localhost:5501", "http://127.0.0.1:5501"})
    void preflightJsonDasOrigensLocais(String origin) throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type, accept"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        verifyNoInteractions(factory);
    }

    @Test void permitePatchDoEndpointExistente() throws Exception {
        mvc.perform(options("/api/gestao/encaminhamentos/1")
                .header("Origin", "http://localhost:5501")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
        verifyNoInteractions(factory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://nao-autorizado.example", "http://localhost:5502", "null"})
    void rejeitaOrigemNaoAutorizada(String origin) throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(factory);
    }

    @Test void rejeitaHeaderNaoAutorizado() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-Nao-Autorizado"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(factory);
    }

    @Test void naoLiberaCaminhoForaDaApi() throws Exception {
        mvc.perform(options("/fora-da-api").header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(factory);
    }

    @Test void respostaDeErroTambemPodeSerLidaPeloApp() throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5500")
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
        verifyNoInteractions(factory);
    }
}
