package br.fiap.daiji;

import br.fiap.daiji.assistant.*;
import br.fiap.daiji.dao.ScoreDAO;
import br.fiap.daiji.factory.ConnectionFactory;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"telegram.bot.enabled=false","gemini.enabled=false"})
@AutoConfigureMockMvc
class JornadaApiTest {
    @Autowired MockMvc mvc;
    @Autowired BeneficiaryJourney journey;
    @Autowired CheckinConversations conversations;
    @Autowired ScoreDAO scores;
    @MockitoBean ConnectionFactory factory;
    static final String DB = "jdbc:h2:mem:cp09;MODE=Oracle;DB_CLOSE_DELAY=-1";
    static final String URL = "/api/beneficiarios/1/medicacoes/1/confirmacoes";
    @BeforeEach void prepare() throws Exception {
        when(factory.conectar()).thenAnswer(call -> DriverManager.getConnection(DB));
        try (Connection c = DriverManager.getConnection(DB); Statement s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            for (String file : new String[]{"/cp06-fixture.sql","/cp09-fixture.sql"})
                try (var reader = new InputStreamReader(getClass().getResourceAsStream(file),StandardCharsets.UTF_8)) { RunScript.execute(c,reader); }
        }
    }
    long count(String table) throws Exception {
        try (Connection c = DriverManager.getConnection(DB); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next(); return rs.getLong(1);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"PENDENTE","CONFIRMADO","PERDIDO"})
    void criaRestIdentityDataStatusFotoNull(String status) throws Exception {
        mvc.perform(post(URL).contentType("application/json").content("{\"statusConfirmacao\":\""+status+"\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.idConfirmacao").value(1))
                .andExpect(jsonPath("$.idBeneficiario").value(1)).andExpect(jsonPath("$.idMedicacao").value(1))
                .andExpect(jsonPath("$.statusConfirmacao").value(status)).andExpect(jsonPath("$.dataConfirmacao").isString())
                .andExpect(jsonPath("$.fotoUrl").doesNotExist());
        try (Connection c = DriverManager.getConnection(DB); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM CONFIRMACAO_MEDICACAO")) {
            assertTrue(rs.next()); assertEquals(status,rs.getString("status_confirmacao")); assertNotNull(rs.getTimestamp("data_confirmacao"));
            assertNull(rs.getString("foto_url")); assertFalse(rs.next());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"{}","null","{\"statusConfirmacao\":null}","{\"statusConfirmacao\":\"TOMOU\"}",
            "{\"statusConfirmacao\":\"NAO_TOMOU\"}","{\"statusConfirmacao\":\"confirmado\"}","{\"statusConfirmacao\":\" CONFIRMADO \"}",
            "{\"statusConfirmacao\":true}","{\"statusConfirmacao\":5}","{\"statusConfirmacao\":\"\"}","malformed",
            "{\"statusConfirmacao\":\"CONFIRMADO\",\"idBeneficiario\":2}","{\"statusConfirmacao\":\"CONFIRMADO\",\"idMedicacao\":2}",
            "{\"statusConfirmacao\":\"CONFIRMADO\",\"fotoUrl\":\"x\"}","{\"statusConfirmacao\":\"CONFIRMADO\",\"dataConfirmacao\":\"2026-09-17\"}"})
    void bodyInvalidoOuConflitante400SemEscrita(String body) throws Exception {
        mvc.perform(post(URL).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertEquals(0,count("CONFIRMACAO_MEDICACAO"));
    }
    @ParameterizedTest @ValueSource(strings = {"/api/beneficiarios/0/medicacoes/1/confirmacoes", "/api/beneficiarios/1/medicacoes/-1/confirmacoes",
            "/api/beneficiarios/abc/medicacoes/1/confirmacoes", "/api/beneficiarios/1/medicacoes/2147483648/confirmacoes"})
    void idsInvalidos400(String url) throws Exception {
        mvc.perform(post(url).contentType("application/json").content("{\"statusConfirmacao\":\"CONFIRMADO\"}")).andExpect(status().isBadRequest());
        assertEquals(0,count("CONFIRMACAO_MEDICACAO"));
    }
    @ParameterizedTest @ValueSource(strings = {"/api/beneficiarios/999/medicacoes/1/confirmacoes", "/api/beneficiarios/1/medicacoes/999/confirmacoes",
            "/api/beneficiarios/1/medicacoes/2/confirmacoes"})
    void inexistenteOuDeOutroBeneficiario404(String url) throws Exception {
        mvc.perform(post(url).contentType("application/json").content("{\"statusConfirmacao\":\"CONFIRMADO\"}")).andExpect(status().isNotFound());
        assertEquals(0,count("CONFIRMACAO_MEDICACAO"));
    }
    @Test void erro500Sanitizado() throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("ORA-123 JDBC URL senha"));
        mvc.perform(post(URL).contentType("application/json").content("{\"statusConfirmacao\":\"CONFIRMADO\"}"))
                .andExpect(status().isInternalServerError()).andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}"));
    }
    @Test void listagemIsoladaNoSqlComDadosDeDoisBeneficiarios() throws Exception {
        mvc.perform(get("/api/beneficiarios/1/medicacoes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].idMedicacao").value(1)).andExpect(jsonPath("$[0].nomeMedicamento").value("Medicamento A"));
        mvc.perform(get("/api/beneficiarios/2/medicacoes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].idMedicacao").value(2)).andExpect(jsonPath("$[0].nomeMedicamento").value("Medicamento B"));
        mvc.perform(get("/api/beneficiarios/3/medicacoes")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void multiplasConfirmacoesPermitidasAdesaoFuturaSemRecalculo() throws Exception {
        long history = count("SCORE_RISCO_RENAL"); var before = scores.atual(1);
        for (String s : new String[]{"CONFIRMADO","CONFIRMADO","PERDIDO","PENDENTE"})
            mvc.perform(post(URL).contentType("application/json").content("{\"statusConfirmacao\":\""+s+"\"}")).andExpect(status().isCreated());
        assertEquals(4,count("CONFIRMACAO_MEDICACAO")); assertEquals(history,count("SCORE_RISCO_RENAL")); assertEquals(before,scores.atual(1));
        var adherence = scores.adesao(1); assertEquals(2,adherence.confirmados()); assertEquals(1,adherence.perdidos());
        assertEquals(0,scores.adesao(2).confirmados());
    }
    @Test void checkinRestMantemAppETelegramUsaCanalOficial() throws Exception {
        mvc.perform(post("/api/beneficiarios/1/checkins").contentType("application/json")
                .content("{\"nivelEstresse\":3,\"canal\":\"TELEGRAM\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canal").value("APP"));
        journey.route(900,"/checkin 1");
        for (String text : new String[]{"3","BOA","BOA","CALMO","PULAR"}) journey.route(900,text);
        assertEquals(1,count("CHECKIN"));
        assertEquals(new AssistantResponse.Text("Check-in registrado com sucesso. ✅"),journey.route(900,"CONFIRMAR"));
        try (Connection c = DriverManager.getConnection(DB); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT canal, nivel_estresse, resposta_texto FROM CHECKIN ORDER BY id_checkin")) {
            rs.next(); assertEquals("APP",rs.getString(1)); rs.next(); assertEquals("TELEGRAM",rs.getString(1));
            assertEquals(3,rs.getInt(2)); assertNull(rs.getString(3)); assertFalse(rs.next());
        }
    }
    @Test void telegramConfirmaViaMesmoServiceERejeitaOutraPessoa() throws Exception {
        assertEquals(new AssistantResponse.Text("Confirmação registrada com sucesso."),journey.route(910,"/confirmar 1 1 CONFIRMADO"));
        assertEquals(1,count("CONFIRMACAO_MEDICACAO"));
        assertEquals(new AssistantResponse.Text("Beneficiário ou medicação não encontrado para os IDs informados."),journey.route(910,"/confirmar 1 2 CONFIRMADO"));
        assertEquals(1,count("CONFIRMACAO_MEDICACAO"));
    }
    @Test void idsVisiveisNoNovoComandoSemMisturarBeneficiarios() {
        var formatter = new ResponseFormatter();
        assertEquals("💊 Medicações cadastradas (IDs)\n\n• ID 1 — Medicamento A — 10 mg — 08:00",formatter.format(journey.route(920,"/medicacoes_ids 1")));
        assertEquals("Nenhuma medicação cadastrada.",formatter.format(journey.route(920,"/medicacoes_ids 3")));
    }
}
