package br.fiap.daiji;

import br.fiap.daiji.dao.ScoreDAO;
import br.fiap.daiji.factory.ConnectionFactory;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Controller, DTO, services, DAOs e SQL reais; somente ConnectionFactory aponta para H2 em memória.
@SpringBootTest(properties = {"telegram.bot.enabled=false", "gemini.enabled=false"})
@AutoConfigureMockMvc
class ScorePublicadoAuditTest {
    @Autowired MockMvc mvc;
    @Autowired ScoreDAO scores;
    @MockitoBean ConnectionFactory factory;
    Connection anchor;
    String database;
    static final String BASE = "/api/beneficiarios/1";

    @BeforeEach void preparar() throws Exception {
        database = "jdbc:h2:mem:audit_" + UUID.randomUUID() + ";MODE=Oracle";
        anchor = DriverManager.getConnection(database);
        when(factory.conectar()).thenAnswer(call -> DriverManager.getConnection(database));
        for (String file : new String[]{"/cp06-fixture.sql", "/cp09-fixture.sql"}) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream(file), StandardCharsets.UTF_8)) {
                RunScript.execute(anchor, reader);
            }
        }
        try (Statement s = anchor.createStatement()) {
            s.execute("CREATE TABLE COMORBIDADE (id_comorbidade NUMBER PRIMARY KEY, descricao VARCHAR2(150))");
            s.execute("CREATE TABLE BENEFICIARIO_COMORBIDADE (id_beneficiario NUMBER, id_comorbidade NUMBER)");
            // Pessoa jovem; demais penalidades zero. Somente o banco efêmero deste teste.
            s.executeUpdate("UPDATE BENEFICIARIO SET data_nascimento = DATE '2005-01-02' WHERE id_beneficiario = 1");
            s.executeUpdate("UPDATE SCORE_RISCO_RENAL SET data_calculo = DATE '2000-01-01'");
        }
    }

    @AfterEach void fechar() throws Exception { if (anchor != null) anchor.close(); }

    void checkin(String json) throws Exception {
        mvc.perform(post(BASE + "/checkins").contentType("application/json").content(json))
            .andExpect(status().isCreated());
    }

    void recalcular(int esperado, int penalidade, boolean suficientes) throws Exception {
        mvc.perform(post(BASE + "/score/recalcular"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.valorScore").value(esperado))
            .andExpect(jsonPath("$.fatores.checkins").value(penalidade))
            .andExpect(jsonPath("$.fatores.dadosCheckinsSuficientes").value(suficientes))
            .andExpect(jsonPath("$.fatores.dadosAdesaoSuficientes").value(false));
        mvc.perform(get(BASE + "/score"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valorScore").value(esperado));
    }

    @ParameterizedTest(name = "estresse={0}, sono={1}, alimentação={2}: score={3}")
    @CsvSource({
        "1,Ótimo,Segui bem minha dieta,100,0",
        "1,Péssimo,Não me alimentei bem,100,0",
        "1,BOM,BOA,100,0",
        "1,BOM,REGULAR,100,0",
        "1,BOM,RUIM,95,5",
        "1,REGULAR,BOA,100,0",
        "1,REGULAR,REGULAR,95,5",
        "1,REGULAR,RUIM,95,5",
        "1,RUIM,BOA,95,5",
        "1,RUIM,REGULAR,95,5",
        "1,RUIM,RUIM,90,10",
        "5,Péssimo,Não me alimentei bem,85,15",
        "5,RUIM,RUIM,85,15"
    })
    void payloadPersistidoRecalculadoEConsultado(int estresse, String sono, String alimentacao,
                                                int esperado, int penalidade) throws Exception {
        checkin("""
            {"nivelEstresse":%d,"qualidadeSono":"%s","qualidadeAlimentacao":"%s",
             "humor":null,"respostaTexto":"Horário de dormir: 22:30 | Medicação: Não tomei hoje"}
            """.formatted(estresse, sono, alimentacao));
        // Nova conexão depois do HTTP 201: prova que houve commit, sem conversão/perda dos campos.
        try (Connection c = DriverManager.getConnection(database); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM CHECKIN WHERE id_beneficiario = 1")) {
            assertTrue(rs.next());
            assertEquals(estresse, rs.getInt("nivel_estresse"));
            assertEquals(sono, rs.getString("qualidade_sono"));
            assertEquals(alimentacao, rs.getString("qualidade_alimentacao"));
            assertNull(rs.getString("humor"));
            assertEquals("APP", rs.getString("canal"));
            assertNotNull(rs.getTimestamp("data_checkin"));
            assertEquals("Horário de dormir: 22:30 | Medicação: Não tomei hoje", rs.getString("resposta_texto"));
            assertFalse(rs.next());
        }
        var lidos = scores.ultimosCheckins(1);
        assertEquals(1, lidos.size());
        assertEquals(estresse, lidos.getFirst().estresse());
        assertEquals(sono, lidos.getFirst().sono());
        assertEquals(alimentacao, lidos.getFirst().alimentacao());
        assertNull(lidos.getFirst().humor());
        assertTrue(scores.ultimosCheckins(2).isEmpty());
        recalcular(esperado, penalidade, true);
    }

    @Test void ausenciaENullNaoGeramPenalidade() throws Exception {
        recalcular(100, 0, false);
        checkin("{\"nivelEstresse\":null,\"qualidadeSono\":null,\"qualidadeAlimentacao\":null,\"humor\":null}");
        var c = scores.ultimosCheckins(1).getFirst();
        assertNull(c.estresse()); assertNull(c.sono()); assertNull(c.alimentacao()); assertNull(c.humor());
        recalcular(100, 0, false);
    }

    @Test void seteMaisRecentesIncluemNovoEDiluemUmRuimMesmoComEstresseCinco() throws Exception {
        checkin("{\"nivelEstresse\":5,\"qualidadeSono\":\"RUIM\",\"qualidadeAlimentacao\":\"RUIM\"}");
        for (int i = 0; i < 6; i++)
            checkin("{\"nivelEstresse\":1,\"qualidadeSono\":\"BOM\",\"qualidadeAlimentacao\":\"BOA\"}");
        checkin("{\"nivelEstresse\":5,\"qualidadeSono\":\"Péssimo\",\"qualidadeAlimentacao\":\"Não me alimentei bem\"}");
        try (Statement s = anchor.createStatement()) {
            s.executeUpdate("UPDATE CHECKIN SET data_checkin = TIMESTAMP '2026-09-20 12:00:00'");
        }
        var lidos = scores.ultimosCheckins(1);
        assertEquals(7, lidos.size());
        assertEquals("Péssimo", lidos.getFirst().sono()); // Desempate por ID inclui o recém-criado.
        assertTrue(lidos.subList(1, 7).stream().allMatch(c -> c.estresse() == 1 && c.sono().equals("BOM")));
        // 3 / (3 + 6 * 7) = 6,67% <= 25%: nenhuma penalidade pela regra atual.
        recalcular(100, 0, true);
    }
}
