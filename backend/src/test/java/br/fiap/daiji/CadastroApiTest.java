package br.fiap.daiji;

import br.fiap.daiji.factory.ConnectionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"telegram.bot.enabled=false","gemini.enabled=false"})
@AutoConfigureMockMvc
class CadastroApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ConnectionFactory factory;
    static final String DB="jdbc:h2:mem:cp10;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
    // Apenas fixture H2 em memória, transcrita do script oficial completo; não é migration.
    @BeforeEach void preparar() throws Exception {
        when(factory.conectar()).thenAnswer(call -> DriverManager.getConnection(DB));
        sql("DROP ALL OBJECTS");
        sql("""
            CREATE TABLE EMPRESA (
              id_empresa NUMBER GENERATED ALWAYS AS IDENTITY,
              cnpj VARCHAR2(14) NOT NULL, razao_social VARCHAR2(150) NOT NULL,
              data_cadastro DATE DEFAULT SYSDATE NOT NULL,
              CONSTRAINT pk_empresa PRIMARY KEY(id_empresa), CONSTRAINT uk_empresa_cnpj UNIQUE(cnpj))
            """);
        sql("""
            CREATE TABLE BENEFICIARIO (
              id_beneficiario NUMBER GENERATED ALWAYS AS IDENTITY, id_empresa NUMBER NOT NULL,
              nome VARCHAR2(150) NOT NULL, cpf VARCHAR2(11) NOT NULL, email VARCHAR2(150),
              data_nascimento DATE NOT NULL, telefone_whatsapp VARCHAR2(20) NOT NULL,
              status_ativo CHAR(1) DEFAULT 'S' NOT NULL, data_cadastro DATE DEFAULT SYSDATE NOT NULL,
              CONSTRAINT pk_beneficiario PRIMARY KEY(id_beneficiario),
              CONSTRAINT uk_beneficiario_cpf UNIQUE(cpf), CONSTRAINT uk_beneficiario_email UNIQUE(email),
              CONSTRAINT ck_beneficiario_status CHECK(status_ativo IN ('S','N')),
              CONSTRAINT fk_beneficiario_empresa FOREIGN KEY(id_empresa) REFERENCES EMPRESA(id_empresa))
            """);
        sql("""
            CREATE TABLE AUTENTICACAO (
              id_autenticacao NUMBER GENERATED ALWAYS AS IDENTITY, id_beneficiario NUMBER,
              email VARCHAR2(150) NOT NULL, senha_hash VARCHAR2(255) NOT NULL, tipo_usuario VARCHAR2(20) NOT NULL,
              ativo CHAR(1) DEFAULT 'S' NOT NULL,
              CONSTRAINT pk_autenticacao PRIMARY KEY(id_autenticacao),
              CONSTRAINT uk_autenticacao_email UNIQUE(email),
              CONSTRAINT ck_autenticacao_tipo CHECK(tipo_usuario IN ('BENEFICIARIO','GESTOR')),
              CONSTRAINT ck_autenticacao_ativo CHECK(ativo IN ('S','N')),
              CONSTRAINT fk_autenticacao_beneficiario FOREIGN KEY(id_beneficiario) REFERENCES BENEFICIARIO(id_beneficiario))
            """);
        sql("INSERT INTO EMPRESA(cnpj,razao_social) VALUES('12345678000100','Empresa Exemplo')");
    }
    void sql(String sql) throws Exception {
        try(Connection c=DriverManager.getConnection(DB); Statement s=c.createStatement()) { s.execute(sql); }
    }
    int contar(String tabela) throws Exception {
        try(Connection c=DriverManager.getConnection(DB); Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM "+tabela)) {
            rs.next(); return rs.getInt(1);
        }
    }
    ObjectNode request() {
        return mapper.createObjectNode().put("idEmpresa",1).put("nome","Maria da Silva").put("cpf","52998224725")
                .put("email","maria@example.com").put("dataNascimento","1996-05-21")
                .put("telefoneWhatsapp","11950000000").put("senha","SenhaExemplo@123");
    }
    ResultActions cadastrar(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/auth/cadastro").contentType("application/json").content(mapper.writeValueAsBytes(body)));
    }
    @Test void cadastroPersistidoELoginImediato() throws Exception {
        var response=cadastrar(request()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoUsuario").value("BENEFICIARIO"))
                .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.senhaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var json=mapper.readTree(response);
        assertEquals(5,json.size());
        assertTrue(json.get("idBeneficiario").intValue()>0);
        assertTrue(json.get("idAutenticacao").intValue()>0);
        verify(factory,times(1)).conectar();
        try(Connection c=DriverManager.getConnection(DB); Statement s=c.createStatement();
            ResultSet rs=s.executeQuery("SELECT b.*, a.id_autenticacao, a.senha_hash, a.ativo, a.tipo_usuario FROM BENEFICIARIO b JOIN AUTENTICACAO a ON a.id_beneficiario=b.id_beneficiario")) {
            assertTrue(rs.next());
            assertEquals(json.get("idBeneficiario").intValue(),rs.getInt("id_beneficiario"));
            assertEquals(json.get("idAutenticacao").intValue(),rs.getInt("id_autenticacao"));
            assertEquals("S",rs.getString("status_ativo")); assertEquals("S",rs.getString("ativo"));
            assertNotNull(rs.getDate("data_cadastro"));
            assertEquals(1,rs.getInt("id_empresa")); assertEquals("52998224725",rs.getString("cpf"));
            assertEquals("11950000000",rs.getString("telefone_whatsapp"));
            assertEquals(Date.valueOf("1996-05-21"),rs.getDate("data_nascimento"));
            assertEquals("BENEFICIARIO",rs.getString("tipo_usuario"));
            assertNotEquals("SenhaExemplo@123",rs.getString("senha_hash"));
            assertTrue(new BCryptPasswordEncoder().matches("SenhaExemplo@123",rs.getString("senha_hash")));
            assertFalse(rs.next());
        }
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"maria@example.com\",\"senha\":\"SenhaExemplo@123\"}"))
                .andExpect(status().isOk()).andExpect(content().json(response,true));
    }
    @Test void empresaInexistente() throws Exception {
        cadastrar(request().put("idEmpresa",999)).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"mensagem\":\"Empresa informada não encontrada.\"}",true));
        assertEquals(0,contar("BENEFICIARIO")); assertEquals(0,contar("AUTENTICACAO"));
    }
    static Stream<Arguments> invalidos() {
        return Stream.of(
            Arguments.of("idEmpresa","0"),Arguments.of("idEmpresa","-1"),Arguments.of("idEmpresa","1.5"),
            Arguments.of("idEmpresa","\"1\""),Arguments.of("idEmpresa","2147483648"),
            Arguments.of("nome","\" \""),Arguments.of("nome","\""+ "a".repeat(151)+"\""),
            Arguments.of("nome","\""+ "á".repeat(76)+"\""),
            Arguments.of("cpf","\"529.982.247-25\""),Arguments.of("cpf","\"123\""),Arguments.of("cpf","\"abcdefghijk\""),
            Arguments.of("email","\"sem-arroba\""),Arguments.of("email","\"a..b@example.com\""),
            Arguments.of("email","\" a@example.com\""),Arguments.of("email","\"a@example.com \""),
            Arguments.of("email","\"a@-example.com\""),Arguments.of("email","\""+ "a".repeat(140)+"@example.com\""),
            Arguments.of("dataNascimento","\"2023-02-29\""),Arguments.of("dataNascimento","\"21/05/1996\""),
            Arguments.of("dataNascimento","\"2999-01-01\""),Arguments.of("dataNascimento","\"0000-01-01\""),
            Arguments.of("telefoneWhatsapp","\"123\""),Arguments.of("telefoneWhatsapp","\"(11)950000000\""),
            Arguments.of("telefoneWhatsapp","\"1234567890123456\""),
            Arguments.of("senha","\"\""),Arguments.of("senha","\"   \""),
            Arguments.of("senha","\""+ "a".repeat(73)+"\""),Arguments.of("senha","\""+ "á".repeat(37)+"\""),
            Arguments.of("nome","123"),Arguments.of("cpf","52998224725"));
    }
    @ParameterizedTest @MethodSource("invalidos")
    void dadosInvalidos(String campo,String valor) throws Exception {
        var body=request(); body.set(campo,mapper.readTree(valor));
        cadastrar(body).andExpect(status().isBadRequest()).andExpect(content().json("{\"mensagem\":\"Dados de cadastro inválidos.\"}",true));
        verifyNoInteractions(factory);
    }
    @ParameterizedTest @ValueSource(strings={"idEmpresa","nome","cpf","email","dataNascimento","telefoneWhatsapp","senha"})
    void obrigatoriosAusentes(String campo) throws Exception {
        var body=request(); body.remove(campo);
        cadastrar(body).andExpect(status().isBadRequest()); verifyNoInteractions(factory);
    }
    @ParameterizedTest @ValueSource(strings={"idEmpresa","nome","cpf","email","dataNascimento","telefoneWhatsapp","senha"})
    void obrigatoriosNulos(String campo) throws Exception {
        cadastrar(request().putNull(campo)).andExpect(status().isBadRequest()); verifyNoInteractions(factory);
    }
    @ParameterizedTest @ValueSource(strings={"idBeneficiario","idAutenticacao","senhaHash","tipoUsuario","statusAtivo","ativo","dataCadastro","medicacoes"})
    void camposExtrasRejeitados(String campo) throws Exception {
        cadastrar(request().put(campo,"qualquer")).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"mensagem\":\"Dados de cadastro inválidos.\"}",true));
        verifyNoInteractions(factory);
    }
    @ParameterizedTest @ValueSource(strings={"{","null","[]","{}",""})
    void corpoInvalido(String body) throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"mensagem\":\"Dados de cadastro inválidos.\"}",true));
        verifyNoInteractions(factory);
    }
    @Test void tipoDeConteudoIncompativelRetorna415() throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(factory);
    }
    @Test void cpfDuplicadoRollbackPrimeiroInsert() throws Exception {
        cadastrar(request()).andExpect(status().isCreated());
        cadastrar(request().put("email","outra@example.com")).andExpect(status().isConflict())
                .andExpect(content().json("{\"mensagem\":\"CPF ou e-mail já cadastrado.\"}",true));
        assertEquals(1,contar("BENEFICIARIO")); assertEquals(1,contar("AUTENTICACAO"));
    }
    @Test void emailDuplicadoBeneficiario() throws Exception {
        cadastrar(request()).andExpect(status().isCreated());
        cadastrar(request().put("cpf","12345678901")).andExpect(status().isConflict());
        assertEquals(1,contar("BENEFICIARIO")); assertEquals(1,contar("AUTENTICACAO"));
    }
    @Test void emailDuplicadoAutenticacaoRollbackSegundoInsert() throws Exception {
        sql("INSERT INTO AUTENTICACAO(email,senha_hash,tipo_usuario) VALUES('maria@example.com','hash-teste','GESTOR')");
        cadastrar(request()).andExpect(status().isConflict())
                .andExpect(content().json("{\"mensagem\":\"CPF ou e-mail já cadastrado.\"}",true));
        assertEquals(0,contar("BENEFICIARIO")); assertEquals(1,contar("AUTENTICACAO"));
    }
    @ParameterizedTest @ValueSource(strings={"BENEFICIARIO","AUTENTICACAO"})
    void falhaPersistenciaNaoUnicidadeRollback(String tabela) throws Exception {
        // Restrição somente na fixture, para induzir falha real em cada INSERT.
        sql("ALTER TABLE "+tabela+" ADD CONSTRAINT teste_falha CHECK(email <> 'maria@example.com')");
        cadastrar(request()).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensagem\":\"Não foi possível concluir o cadastro.\"}",true));
        assertEquals(0,contar("BENEFICIARIO")); assertEquals(0,contar("AUTENTICACAO"));
    }
    @Test void concorrenciaConstraintGaranteApenasUmCadastro() throws Exception {
        var inicio=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> tarefa=() -> { inicio.await(); return cadastrar(request()).andReturn().getResponse().getStatus(); };
            var a=pool.submit(tarefa); var b=pool.submit(tarefa); inicio.countDown();
            assertEquals(List.of(201,409),Stream.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1,contar("BENEFICIARIO")); assertEquals(1,contar("AUTENTICACAO"));
    }
    @Test void preservaEmailESenhaNoLimiteUtf8() throws Exception {
        String senha=" á".repeat(24); // 72 bytes, incluindo espaços preservados.
        cadastrar(request().put("email","Maria@Example.com").put("senha",senha)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("Maria@Example.com"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsBytes(mapper.createObjectNode().put("email","Maria@Example.com").put("senha",senha))))
                .andExpect(status().isOk());
    }
    @Test void empresasMinimasOrdenadas() throws Exception {
        sql("INSERT INTO EMPRESA(cnpj,razao_social) VALUES('99999999000100','A Empresa')");
        mvc.perform(get("/api/empresas")).andExpect(status().isOk())
                .andExpect(content().json("[{\"idEmpresa\":2,\"nome\":\"A Empresa\"},{\"idEmpresa\":1,\"nome\":\"Empresa Exemplo\"}]",true));
        assertEquals(0,contar("BENEFICIARIO"));
    }
    @Test void empresasVazias() throws Exception {
        sql("DELETE FROM EMPRESA");
        mvc.perform(get("/api/empresas")).andExpect(status().isOk()).andExpect(content().json("[]",true));
    }
    @Test void erroConexaoSanitizado() throws Exception {
        when(factory.conectar()).thenThrow(new SQLException("segredo SQL senha"));
        cadastrar(request()).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensagem\":\"Não foi possível concluir o cadastro.\"}",true));
        mvc.perform(get("/api/empresas")).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensagem\":\"Não foi possível consultar o banco de dados.\"}",true));
    }
}
