package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GestaoApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionFactory factory;
    Connection banco;
    String url;
    static final String BASE = "/api/gestao";

    @BeforeEach void preparar() throws Exception {
        // ConnectionFactory sempre substituída: nenhum caminho pode abrir Oracle.
        url = "jdbc:h2:mem:cp06_" + UUID.randomUUID() + ";MODE=Oracle";
        banco = DriverManager.getConnection(url);
        when(factory.conectar()).thenAnswer(inv -> DriverManager.getConnection(url));
        try (var input = getClass().getResourceAsStream("/cp06-fixture.sql")) {
            assertNotNull(input);
            for (String sql : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) executar(sql);
            }
        }
    }

    void executar(String sql) throws SQLException {
        try (Statement s = banco.createStatement()) { s.execute(sql); }
    }

    long quantidade(String tabela) throws SQLException {
        try (Statement s = banco.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            rs.next(); return rs.getLong(1);
        }
    }

    @AfterEach void fechar() throws Exception { if (banco != null) banco.close(); }

    @Test void dashboardGlobalHistoricoNaoDuplicaPessoasNemMediaOuPendencias() throws Exception {
        mvc.perform(get(BASE + "/dashboard")).andExpect(status().isOk()).andExpect(content().json("""
            {"idEmpresa":null,"totalBeneficiarios":7,"beneficiariosComScore":5,"beneficiariosSemScore":2,
             "baixoRisco":2,"medioRisco":1,"altoRisco":2,"scoreMedio":56.40,"encaminhamentosPendentes":3}
            """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void dashboardEmpresaComMediaArredondada() throws Exception {
        mvc.perform(get(BASE + "/dashboard").param("idEmpresa","1")).andExpect(status().isOk())
            .andExpect(content().json("""
            {"idEmpresa":1,"totalBeneficiarios":4,"beneficiariosComScore":3,"beneficiariosSemScore":1,
             "baixoRisco":1,"medioRisco":1,"altoRisco":1,"scoreMedio":60.67,"encaminhamentosPendentes":2}
            """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void dashboardSemScoreMantemPopulacaoEMediaNull() throws Exception {
        executar("DELETE FROM SCORE_RISCO_RENAL");
        mvc.perform(get(BASE + "/dashboard")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBeneficiarios").value(7))
            .andExpect(jsonPath("$.beneficiariosComScore").value(0))
            .andExpect(jsonPath("$.beneficiariosSemScore").value(7))
            .andExpect(jsonPath("$.baixoRisco").value(0)).andExpect(jsonPath("$.medioRisco").value(0))
            .andExpect(jsonPath("$.altoRisco").value(0)).andExpect(jsonPath("$.scoreMedio").value(nullValue()));
    }

    @Test void empresaExistenteVaziaRetornaZerosEListasVazias() throws Exception {
        mvc.perform(get(BASE + "/dashboard").param("idEmpresa","3")).andExpect(status().isOk())
            .andExpect(content().json("""
            {"idEmpresa":3,"totalBeneficiarios":0,"beneficiariosComScore":0,"beneficiariosSemScore":0,
             "baixoRisco":0,"medioRisco":0,"altoRisco":0,"scoreMedio":null,"encaminhamentosPendentes":0}
            """, org.springframework.test.json.JsonCompareMode.STRICT));
        for (String rota : new String[]{"/beneficiarios", "/casos-atencao", "/encaminhamentos"})
            mvc.perform(get(BASE + rota).param("idEmpresa","3")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest @ValueSource(strings = {"dashboard","beneficiarios","casos-atencao","encaminhamentos"})
    void empresaInexistente404(String rota) throws Exception {
        mvc.perform(get(BASE + "/" + rota).param("idEmpresa","999")).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"","0","-1","abc","2147483648"})
    void empresaInvalida400SemJdbc(String id) throws Exception {
        for (String rota : new String[]{"dashboard","beneficiarios","casos-atencao","encaminhamentos"})
            mvc.perform(get(BASE + "/" + rota).param("idEmpresa",id)).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @Test void beneficiariosGlobalOrdenadoSemDuplicatasESemScoreNulo() throws Exception {
        mvc.perform(get(BASE + "/beneficiarios")).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].idBeneficiario").value(contains(5,2,3,6,1,7,4)))
            .andExpect(jsonPath("$[1].valorScore").value(25))
            .andExpect(jsonPath("$[4].valorScore").value(97))
            .andExpect(jsonPath("$[4].dataCalculoScore").value("2026-09-16T12:30:45"))
            .andExpect(jsonPath("$[5].statusAtivo").value("N"))
            .andExpect(jsonPath("$[6].valorScore").value(nullValue()))
            .andExpect(jsonPath("$[6].classificacaoRisco").value(nullValue()))
            .andExpect(jsonPath("$[6].dataCalculoScore").value(nullValue()));
    }

    @Test void beneficiariosEmpresaFiltrada() throws Exception {
        mvc.perform(get(BASE + "/beneficiarios").param("idEmpresa","1")).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].idBeneficiario").value(contains(2,3,1,4)))
            .andExpect(jsonPath("$[*].idEmpresa").value(everyItem(is(1))));
    }

    @Test void ordenacaoEmpataPorNomeDepoisId() throws Exception {
        executar("UPDATE SCORE_RISCO_RENAL SET valor_score=25 WHERE id_beneficiario=5");
        executar("UPDATE BENEFICIARIO SET nome='Maria Silva' WHERE id_beneficiario=5");
        mvc.perform(get(BASE + "/casos-atencao")).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].idBeneficiario").value(contains(2,5)));
    }

    @Test void atencaoSomenteAltoAtualEFiltradaPorEmpresa() throws Exception {
        mvc.perform(get(BASE + "/casos-atencao")).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].idBeneficiario").value(contains(5,2)))
            .andExpect(jsonPath("$[*].classificacaoRisco").value(everyItem(is("ALTO"))));
        mvc.perform(get(BASE + "/casos-atencao").param("idEmpresa","1")).andExpect(status().isOk())
            .andExpect(content().json("""
            [{"idBeneficiario":2,"idEmpresa":1,"nome":"Maria Silva","email":"maria@daiji.com",
              "statusAtivo":"S","valorScore":25.00,"classificacaoRisco":"ALTO","dataCalculoScore":"2026-09-16T12:30:45"}]
            """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void mesmoInstanteDesempataIdAntesDeFiltrarAlto() throws Exception {
        executar("""
            INSERT INTO SCORE_RISCO_RENAL (id_beneficiario,data_calculo,valor_score,classificacao_risco)
            VALUES (2,TIMESTAMP '2026-09-16 12:30:45',90,'BAIXO')
            """);
        mvc.perform(get(BASE + "/casos-atencao").param("idEmpresa","1"))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void semScoreNaoHaCasosAtencao() throws Exception {
        executar("DELETE FROM SCORE_RISCO_RENAL");
        mvc.perform(get(BASE + "/casos-atencao")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void leiturasNaoRecalculamScoreNemGeramEncaminhamentos() throws Exception {
        for (String rota : new String[]{"dashboard","beneficiarios","casos-atencao","profissionais","encaminhamentos"})
            mvc.perform(get(BASE + "/" + rota)).andExpect(status().isOk());
        assertEquals(8, quantidade("SCORE_RISCO_RENAL"));
        assertEquals(6, quantidade("ENCAMINHAMENTO"));
    }

    @Test void profissionaisOrdenados() throws Exception {
        mvc.perform(get(BASE + "/profissionais")).andExpect(status().isOk()).andExpect(content().json("""
            [{"idProfissional":2,"nome":"Dra. Ana Souza","crm":"CRM-2","especialidade":"Nefrologia"},
             {"idProfissional":1,"nome":"Dra. Zelia","crm":"CRM-1","especialidade":"Nefrologia"}]
            """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void profissionaisVazio() throws Exception {
        executar("DELETE FROM ENCAMINHAMENTO"); executar("DELETE FROM PROFISSIONAL_SAUDE");
        mvc.perform(get(BASE + "/profissionais")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void criaParaBaixoRiscoComIdentityDefaultsENull() throws Exception {
        mvc.perform(post(BASE + "/beneficiarios/1/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idProfissional\":2}")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.idEncaminhamento").value(7)).andExpect(jsonPath("$.idBeneficiario").value(1))
            .andExpect(jsonPath("$.nomeBeneficiario").value("Rafael Almeida"))
            .andExpect(jsonPath("$.idProfissional").value(2)).andExpect(jsonPath("$.nomeProfissional").value("Dra. Ana Souza"))
            .andExpect(jsonPath("$.especialidade").value("Nefrologia"))
            .andExpect(jsonPath("$.statusEncaminhamento").value("PENDENTE"))
            .andExpect(jsonPath("$.dataEncaminhamento").isString()).andExpect(jsonPath("$.dataConsulta").value(nullValue()));
        assertEquals(7, quantidade("ENCAMINHAMENTO"));
        assertEquals(8, quantidade("SCORE_RISCO_RENAL"));
    }

    @Test void criaSemScoreComDataOracleAteSegundos() throws Exception {
        mvc.perform(post(BASE + "/beneficiarios/4/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idProfissional\":2,\"dataConsulta\":\"2026-09-20T14:00:01.123\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.statusEncaminhamento").value("PENDENTE"))
            .andExpect(jsonPath("$.dataConsulta").value("2026-09-20T14:00:01"));
    }

    @Test void beneficiarioInexistente404NaoInsere() throws Exception {
        mvc.perform(post(BASE + "/beneficiarios/999/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idProfissional\":2}")).andExpect(status().isNotFound());
        assertEquals(6, quantidade("ENCAMINHAMENTO"));
    }

    @Test void profissionalInexistente404NaoInsere() throws Exception {
        mvc.perform(post(BASE + "/beneficiarios/1/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idProfissional\":999}")).andExpect(status().isNotFound());
        assertEquals(6, quantidade("ENCAMINHAMENTO"));
    }

    @ParameterizedTest @ValueSource(strings = {"0","-1","abc","2147483648"})
    void idBeneficiarioInvalido400(String id) throws Exception {
        mvc.perform(post(BASE + "/beneficiarios/" + id + "/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idProfissional\":2}")).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @ParameterizedTest @ValueSource(strings = {"{}","null","{\"idProfissional\":null}","{\"idProfissional\":0}",
        "{\"idProfissional\":-1}","{\"idProfissional\":\"abc\"}","{\"idProfissional\":2147483648}",
        "{\"idProfissional\":2,\"dataConsulta\":\"invalida\"}","{\"idProfissional\":2,\"dataConsulta\":\"2026-02-30T14:00:00\"}"})
    void criacaoBodyInvalido400(String body) throws Exception {
        mvc.perform(post(BASE + "/beneficiarios/1/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
            .content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @ParameterizedTest @ValueSource(strings = {"1.5", "2.9"})
    void idProfissionalFracionarioNaoPodeCriarParaOutraPessoa(String id) throws Exception {
        mvc.perform(post(BASE + "/beneficiarios/1/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idProfissional\":" + id + "}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @Test void listaEncaminhamentosGlobalOrdenacaoEJoins() throws Exception {
        mvc.perform(get(BASE + "/encaminhamentos")).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].idEncaminhamento").value(contains(6,5,4,3,2,1)))
            .andExpect(jsonPath("$[5].nomeBeneficiario").value("Maria Silva"))
            .andExpect(jsonPath("$[5].nomeProfissional").value("Dra. Ana Souza"))
            .andExpect(jsonPath("$[5].dataEncaminhamento").value("2026-09-16T10:00:00"))
            .andExpect(jsonPath("$[5].dataConsulta").value(nullValue()));
    }

    @ParameterizedTest @CsvSource({"PENDENTE,3","AGENDADO,1","REALIZADO,1","CANCELADO,1"})
    void filtraCadaStatus(String status, int total) throws Exception {
        mvc.perform(get(BASE + "/encaminhamentos").param("status",status)).andExpect(status().isOk())
            .andExpect(jsonPath("$",hasSize(total)))
            .andExpect(jsonPath("$[*].statusEncaminhamento").value(everyItem(is(status))));
    }

    @Test void filtrosEmpresaEStatusCombinados() throws Exception {
        mvc.perform(get(BASE + "/encaminhamentos").param("idEmpresa","1").param("status","PENDENTE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].idEncaminhamento").value(contains(2,1)));
        mvc.perform(get(BASE + "/encaminhamentos").param("idEmpresa","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].idEncaminhamento").value(contains(6,5)));
    }

    @ParameterizedTest @ValueSource(strings = {"","invalido","pendente","PENDENTE' OR 1=1 --"})
    void filtroStatusInvalido400(String valor) throws Exception {
        mvc.perform(get(BASE + "/encaminhamentos").param("status",valor)).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @Test void agendaEPersisteDataComHora() throws Exception {
        mvc.perform(patch(BASE + "/encaminhamentos/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"statusEncaminhamento\":\"AGENDADO\",\"dataConsulta\":\"2026-09-20T14:30:15.999\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.statusEncaminhamento").value("AGENDADO"))
            .andExpect(jsonPath("$.dataConsulta").value("2026-09-20T14:30:15"));
        mvc.perform(get(BASE + "/encaminhamentos").param("status","AGENDADO"))
            .andExpect(jsonPath("$[*].idEncaminhamento").value(contains(3,1)));
    }

    @ParameterizedTest @ValueSource(strings = {"PENDENTE","REALIZADO","CANCELADO"})
    void atualizaStatusSemDataELimpaDataAnterior(String valor) throws Exception {
        mvc.perform(patch(BASE + "/encaminhamentos/3").contentType(MediaType.APPLICATION_JSON)
            .content("{\"statusEncaminhamento\":\"" + valor + "\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.statusEncaminhamento").value(valor))
            .andExpect(jsonPath("$.dataConsulta").value(nullValue()));
    }

    @Test void realizaComDataSemInventarInformacoesClinicas() throws Exception {
        mvc.perform(patch(BASE + "/encaminhamentos/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"statusEncaminhamento\":\"REALIZADO\",\"dataConsulta\":\"2026-09-20T14:00:00\"}"))
            .andExpect(status().isOk()).andExpect(content().json("""
            {"idEncaminhamento":1,"idBeneficiario":2,"nomeBeneficiario":"Maria Silva","idProfissional":2,
             "nomeProfissional":"Dra. Ana Souza","especialidade":"Nefrologia","dataEncaminhamento":"2026-09-16T10:00:00",
             "dataConsulta":"2026-09-20T14:00:00","statusEncaminhamento":"REALIZADO"}
            """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void encaminhamentoInexistente404() throws Exception {
        mvc.perform(patch(BASE + "/encaminhamentos/999").contentType(MediaType.APPLICATION_JSON)
            .content("{\"statusEncaminhamento\":\"CANCELADO\"}")).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings = {"0","-1","abc","9223372036854775808"})
    void idEncaminhamentoInvalido400(String id) throws Exception {
        mvc.perform(patch(BASE + "/encaminhamentos/" + id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"statusEncaminhamento\":\"CANCELADO\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @ParameterizedTest @ValueSource(strings = {"{}","null","{\"statusEncaminhamento\":null}",
        "{\"statusEncaminhamento\":\"INVALIDO\"}","{\"statusEncaminhamento\":\"agendado\"}",
        "{\"statusEncaminhamento\":\"AGENDADO\"}","{\"statusEncaminhamento\":\"AGENDADO\",\"dataConsulta\":null}",
        "{\"statusEncaminhamento\":\"AGENDADO\",\"dataConsulta\":\"2026-02-30T14:00:00\"}",
        "{\"statusEncaminhamento\":\"AGENDADO\",\"dataConsulta\":\"0000-01-01T14:00:00\"}",
        "{\"statusEncaminhamento\":\"AGENDADO\",\"dataConsulta\":\"+10000-01-01T14:00:00\"}"})
    void atualizacaoBodyInvalido400(String body) throws Exception {
        mvc.perform(patch(BASE + "/encaminhamentos/1").contentType(MediaType.APPLICATION_JSON)
            .content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(factory);
    }

    @ParameterizedTest @ValueSource(strings = {"dashboard","beneficiarios","casos-atencao","profissionais","encaminhamentos"})
    void falhaJdbc500SemDetalhes(String rota) throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("ORA-00942 SQL jdbc:oracle: usuario senha stack"));
        mvc.perform(get(BASE + "/" + rota)).andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}",
                org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void falhaJdbcNaCriacao500Generico() throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("ORA-00942 SQL jdbc:oracle: usuario senha"));
        mvc.perform(post(BASE + "/beneficiarios/1/encaminhamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"idProfissional\":2}")).andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}",
                org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void falhaJdbcNaAtualizacao500Generico() throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("ORA-00942 SQL jdbc:oracle: usuario senha"));
        mvc.perform(patch(BASE + "/encaminhamentos/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"statusEncaminhamento\":\"CANCELADO\"}")).andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}",
                org.springframework.test.json.JsonCompareMode.STRICT));
    }}

