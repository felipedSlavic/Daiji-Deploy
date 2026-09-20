# DAIJI — Sprint 4, checkpoints 1, 2 e 3

Auditoria atual do backend (20/09/2026): [contratos REST, build/testes, configuração de PORT/CORS e pendências de produção](docs/auditoria-backend-2026-09-20.md). As seções abaixo preservam o histórico dos checkpoints.

Backend acadêmico Java 21 + Maven + Spring Boot 3.5.16 + Spring Web + Oracle JDBC 23.26.3.0.0. JDBC puro, sem JPA, Hibernate ou JdbcTemplate.

## Execução

Configure JDK 21 como SDK do projeto e como JVM do Maven. No terminal, confira `java -version` e `mvn -version`. Nesta máquina o Java padrão do PATH é 8; existe um JDK 21 em `C:\Users\felip\.jdks\ms-21.0.10`.

Defina no ambiente do processo (terminal ou configuração de execução do IntelliJ):

| Variável | Valor |
| --- | --- |
| ORACLE_URL | URL JDBC do banco existente, no formato `jdbc:oracle:thin:@//HOST:1521/SERVICE_NAME` (ou a URL SID já utilizada) |
| ORACLE_USER | Usuário Oracle |
| ORACLE_PASSWORD | Senha Oracle |

Não grave valores reais no código. Arquivos `.env` não são carregados automaticamente pelo Spring Boot. A aplicação exige as três configurações ao iniciar; a conexão física é aberta pelo DAO a cada chamada e fechada por try-with-resources.

Na pasta que contém o pom.xml:

```text
mvn verify
mvn spring-boot:run
```

Alternativamente, após `mvn package`:

```text
java -jar target/Daiji-Sprint04-1.0-SNAPSHOT.jar
```

## Endpoint

```text
GET http://localhost:8080/api/beneficiarios/7
```

Use um ID existente no seu Oracle. Exemplo fictício de HTTP 200:

```json
{
  "id": 7,
  "idEmpresa": 2,
  "nome": "Pessoa de Teste",
  "cpf": "00000000000",
  "email": null,
  "dataNascimento": "2000-01-02",
  "telefone": "11900000000",
  "status": "S",
  "dataCadastro": "2026-09-15"
}
```

- 200: beneficiário encontrado, JSON.
- 404: registro ausente, corpo vazio.
- 400: ID incompatível com Integer, por exemplo `abc`.
- 500: erro JDBC, JSON com mensagem genérica, sem detalhes SQL.

O DTO retorna somente o ID da empresa porque a consulta não carrega os demais dados de EMPRESA. Datas seguem os models existentes (`LocalDate`), portanto dataCadastro continua sem horário.

## Arquitetura

```text
br.fiap.daiji
├── controller
│   ├── BeneficiarioController
│   └── ApiExceptionHandler
├── service
│   └── BeneficiarioService
├── dao
│   ├── BeneficiarioDAO
│   ├── EmpresaDAO
│   ├── CheckinDAO
│   ├── MedicacaoDAO
│   └── GenericDAO
├── model
│   ├── Beneficiario
│   ├── Empresa
│   ├── Checkin
│   └── Medicacao
├── dto
│   └── BeneficiarioResponse
├── factory
│   └── ConnectionFactory
└── DaijiApplication
```

Fluxo: Controller → Service → BeneficiarioDAO → ConnectionFactory/JDBC → Oracle. O Service retorna Optional; o Controller converte ausência em 404 e o model em DTO. O DAO usa SELECT com colunas explícitas e `WHERE id_beneficiario = ?`.

## Escopo e banco

A consulta individual, o login e a criação de check-in foram expostos. Score, Telegram, dashboard, encaminhamento e medicação permanecem fora do escopo. Models e operações JDBC legadas da Sprint 3 foram preservados. O CheckinDAO agora também atende o fluxo REST descrito no Checkpoint 3 ao final deste documento; as seções anteriores registram o histórico dos checkpoints.

Nenhuma alteração de banco é necessária para esta consulta. Nenhum DDL, DML, migração ou seed é executado ao iniciar. `scriptSql` é referência histórica da Sprint 3, fora dos recursos da aplicação: não deve ser executado sobre o banco existente. O texto SQL anexado foi usado como referência de esquema, não como instrução de execução. A única alteração no script histórico foi retirar um comentário que continha credenciais.

## Testes

`BeneficiarioApiTest` sobe o contexto Spring e usa Controller, Service e DAO reais, simulando somente ConnectionFactory e objetos JDBC. Verifica JSON 200, 404, erro de consulta 500, erro de conexão 500, ID inválido 400 e ausência de cadastro HTTP; também verifica parâmetro SQL e fechamento dos recursos.

Esses testes não certificam conectividade, permissões ou dados do Oracle real. A validação com Oracle deve ser feita com as variáveis acima e um ID conhecido. Não foi usado banco alternativo.

## Análise da Sprint 3

Foram revisados pom.xml, README, script SQL, configuração, Main, cinco telas, quatro models, quatro DAOs, GenericDAO e o teste original. O ZIP também contém metadados Git/IntelliJ e classes compiladas, que não entram na distribuição nova.

- Main iniciava MenuPrincipal/JOptionPane; telas chamavam DAOs diretamente.
- DAOs já usavam PreparedStatement e try-with-resources.
- BeneficiarioDAO tinha inserir/listar/atualizar/excluir, sem busca por ID.
- Factory estática carregava database.properties com credenciais fixas.
- Os métodos legados capturam SQLException e imprimem no console. Foram preservados, mas o novo buscarPorId propaga a falha para produzir HTTP 500, evitando falso 404.
- Models correspondem ao subconjunto da Sprint 3 e já representam as colunas usadas nesta consulta. Nenhuma entidade adicional é necessária.
- O teste JUnit 3 apenas verificava assertTrue(true); foi substituído por testes do fluxo REST.
- O SQL novo inclui tabelas adicionais e TELEGRAM como canal; essas diferenças não afetam GET beneficiários e não foram implementadas.

## Arquivos em relação ao ZIP original

Criados:
- `src/main/java/br/fiap/daiji/DaijiApplication.java`
- `src/main/java/br/fiap/daiji/controller/BeneficiarioController.java`
- `src/main/java/br/fiap/daiji/controller/ApiExceptionHandler.java`
- `src/main/java/br/fiap/daiji/service/BeneficiarioService.java`
- `src/main/java/br/fiap/daiji/dto/BeneficiarioResponse.java`
- `src/main/resources/application.properties`
- `src/test/java/br/fiap/daiji/BeneficiarioApiTest.java`

Alterados:
- `pom.xml`: Spring Boot/Web, driver Oracle, Java 21, testes e JAR executável.
- `src/main/java/br/fiap/daiji/factory/ConnectionFactory.java`: componente com configuração externa.
- `src/main/java/br/fiap/daiji/dao/BeneficiarioDAO.java`: construtor/conexão e busca por ID.
- `src/main/java/br/fiap/daiji/dao/EmpresaDAO.java`, `CheckinDAO.java` e `MedicacaoDAO.java`: apenas construtor/conexão.
- `.gitignore`: ignora configurações locais com possíveis segredos.
- `README.md`: análise, configuração, contrato, testes e inventário.
- `scriptSql`: removido somente comentário com credenciais; DDL preservado.

Removidos da versão Sprint 4:
- `src/main/java/br/fiap/daiji/Main.java`
- `src/main/java/br/fiap/daiji/view/MenuPrincipal.java`
- `src/main/java/br/fiap/daiji/view/MenuBeneficiario.java`
- `src/main/java/br/fiap/daiji/view/MenuEmpresa.java`
- `src/main/java/br/fiap/daiji/view/MenuCheckin.java`
- `src/main/java/br/fiap/daiji/view/MenuMedicacao.java`
- `src/main/resources/database.properties`
- `src/test/java/org/example/AppTest.java`

Os quatro models e GenericDAO foram preservados integralmente. `.git/`, `.idea/` e `target/` antigos não foram copiados. O ZIP de entrada não foi alterado.

## Resultado da validação local

Em 15/09/2026, Maven verify com JDK 21.0.10: BUILD SUCCESS; 6 testes, 0 falhas, 0 erros, 0 ignorados. JAR executável gerado. Oracle real não acessado.


## Checkpoint 2 — login real

O escopo atual inclui a consulta do Checkpoint 1 e o login abaixo. As seções anteriores registram o histórico do Checkpoint 1.

`POST /api/auth/login`, com `Content-Type: application/json`:

```json
{"email":"rafael@daiji.com","senha":"Daiji@123"}
```

Resposta 200 para beneficiário (idAutenticacao é gerado pelo Oracle):

```json
{"idAutenticacao":1,"idBeneficiario":1,"nome":"Rafael Almeida","email":"rafael@daiji.com","tipoUsuario":"BENEFICIARIO"}
```

Para GESTOR, `idBeneficiario` e `nome` são `null`. Email e tipo vêm de AUTENTICACAO. A busca usa o email recebido, sem conversão de maiúsculas/minúsculas. O nome é consultado pelo `BeneficiarioDAO.buscarPorId` existente.

- 200: email encontrado, ativo S, tipo permitido e senha validada com BCrypt.
- 401: email inexistente, senha incorreta, inativo, tipo inválido ou beneficiário sem vínculo válido. Mensagem única: `{"mensagem":"Credenciais inválidas."}`.
- 401 também para email/senha ausentes ou vazios e senha maior que 72 bytes UTF-8 (limite do BCrypt).
- 400: corpo ausente ou JSON malformado, pelo tratamento padrão do Spring Web.
- 500: SQLException mantém a mensagem genérica existente, sem detalhes do banco.

Fluxo: AuthController → AuthService → AutenticacaoDAO / BeneficiarioDAO → ConnectionFactory → Oracle. Somente `spring-security-crypto` foi adicionada (6.5.11, gerenciada pelo Spring Boot). O login não cria token ou sessão. Nenhuma configuração de segurança altera o GET existente.

### Gerar hashes manualmente

Com Java 21 e Maven no PATH, execute em PowerShell na raiz do projeto:

```powershell
mvn dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt'
$classpath = (Get-Content -LiteralPath target/classpath.txt -Raw).Trim()
java --class-path "$classpath" tools/GerarHashBCrypt.java
```

Digite a senha no prompt oculto. O utilitário imprime somente o hash, não inicia o Spring e não acessa o banco. Cada execução gera um hash diferente devido ao salt aleatório.

### INSERTs para execução manual no Oracle

Os hashes abaixo foram gerados com BCrypt e conferidos com `matches`. Correspondem, respectivamente, às senhas demo `Daiji@123` e `Gestor@123`. O beneficiário de ID 1 precisa existir e os emails ainda não podem estar cadastrados em AUTENTICACAO. Nenhum comando abaixo foi executado automaticamente.

```sql
INSERT INTO AUTENTICACAO
    (id_beneficiario, email, senha_hash, tipo_usuario, ativo)
VALUES
    (1, 'rafael@daiji.com',
     '$2a$10$lEH5rb1QTHH4KrWktcVsgOcZAxm2yeVihAzoBYnXhUcPT9ay83H32',
     'BENEFICIARIO', 'S');

INSERT INTO AUTENTICACAO
    (id_beneficiario, email, senha_hash, tipo_usuario, ativo)
VALUES
    (NULL, 'gestor@daiji.com',
     '$2a$10$W8QrqRSRq.xIPfjlCC.ejuqv5x0le9CV/0Pcl8fnJDWl4nuIbjEle',
     'GESTOR', 'S');

COMMIT;
```

### Arquivos e testes do Checkpoint 2

Criados em `src/main/java/br/fiap/daiji`: `controller/AuthController.java`, `service/AuthService.java`, `service/CredenciaisInvalidasException.java`, `dao/AutenticacaoDAO.java`, `model/Autenticacao.java`, `dto/LoginRequest.java` e `dto/LoginResponse.java`.

Também criados: `src/test/java/br/fiap/daiji/AuthApiTest.java` e `tools/GerarHashBCrypt.java`.

Alterados: `pom.xml` (crypto), `ApiExceptionHandler.java` (401 genérico) e este README. Schema, `scriptSql`, configurações Oracle e implementação/testes do Checkpoint 1 preservados.

`AuthApiTest` usa Controller, Service, DAOs e BCrypt reais, simulando apenas a fronteira JDBC. Cobre beneficiário válido, senha incorreta, email inexistente, inativo, gestor com vínculo Oracle NULL, tipo inválido, beneficiário sem vínculo, rejeição de senha armazenada em texto puro, campos ausentes e falha SQL. Verifica parâmetros e fechamento dos recursos. Os testes não acessam Oracle real.

Validação do Checkpoint 2 em 15/09/2026: Maven verify com Java 21.0.10, BUILD SUCCESS e JAR executável gerado. Total: 16 testes, 0 falhas, 0 erros e 0 ignorados (10 de login + 6 do Checkpoint 1). Oracle real não acessado.

## Checkpoint 3 — criação de check-in

Fluxo: CheckinController → CheckinService → CheckinDAO → ConnectionFactory → Oracle, com JDBC puro. Reutiliza o model Checkin e BeneficiarioDAO.buscarPorId. O método legado inserir permanece preservado; o REST chama criar, que propaga SQLException ao tratamento genérico existente.

### Schema encontrado no projeto

Fonte: `scriptSql`, linhas 44–57 e 75–77. O banco real não foi consultado nesta implementação.

| Coluna | Definição |
| --- | --- |
| id_checkin | NUMBER GENERATED ALWAYS AS IDENTITY, PK |
| id_beneficiario | NUMBER(9) NOT NULL, FK para BENEFICIARIO(id_beneficiario) |
| data_checkin | TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL |
| canal | VARCHAR2(20) NOT NULL, apenas WHATSAPP, APP ou SMS |
| nivel_estresse | NUMBER(1), opcional, entre 1 e 5 |
| qualidade_sono | VARCHAR2(20), opcional |
| qualidade_alimentacao | VARCHAR2(30), opcional |
| humor | VARCHAR2(20), opcional |
| resposta_texto | VARCHAR2(500), opcional |

Este script não aceita WEB nem TELEGRAM. A API fixa APP. Não foram alterados schema, script SQL, model, dependências ou configurações Oracle.

### Contrato HTTP

`POST /api/beneficiarios/7/checkins`, `Content-Type: application/json`:

```json
{
  "nivelEstresse": 3,
  "qualidadeSono": "BOA",
  "qualidadeAlimentacao": "BOA",
  "humor": "CALMO",
  "respostaTexto": "Dia tranquilo"
}
```

Todos os cinco campos são opcionais, conforme o schema; `{}` também é válido. Estresse, quando informado, precisa ser um número inteiro JSON de 1 a 5 (frações, booleanos e strings são rejeitados). Sono, alimentação e humor são textos livres, sem enum inventado. Textos vazios viram null, como ocorre no Oracle. ID do beneficiário deve estar entre 1 e 999999999 e vem exclusivamente da URL. Campos desconhecidos no JSON são ignorados pelo comportamento existente do Spring/Jackson; enviar idBeneficiario, idCheckin, dataCheckin ou canal não substitui os valores controlados pela API/banco.

Os limites de texto são 20/30/20/500 bytes UTF-8, respectivamente. É uma validação conservadora: o DDL não explicita BYTE/CHAR nem o charset do banco, e caracteres acentuados podem consumir mais de um byte. Em um banco com semântica CHAR, a API pode rejeitar textos que caberiam na coluna.

Exemplo de resposta HTTP 201 (ID e data ilustrativos, gerados pelo Oracle):

```json
{
  "idCheckin": 42,
  "idBeneficiario": 7,
  "dataCheckin": "2026-09-16T10:30:00",
  "canal": "APP",
  "nivelEstresse": 3,
  "qualidadeSono": "BOA",
  "qualidadeAlimentacao": "BOA",
  "humor": "CALMO",
  "respostaTexto": "Dia tranquilo"
}
```

- 201: registro criado, com JSON acima; campos opcionais não informados retornam null.
- 400: ID inválido, estresse inválido, texto acima do limite, corpo ausente ou JSON inválido.
- 404: beneficiário não encontrado pelo DAO existente.
- 500: falha JDBC, com o JSON genérico preservado: `{"mensagem":"Não foi possível consultar o banco de dados."}`. Não expõe mensagens Oracle.

Não foi criado endpoint GET de check-in. dataCheckin é LocalDateTime porque a coluna é TIMESTAMP sem fuso horário.

### IDENTITY e transação

O INSERT omite id_checkin e data_checkin, respeitando IDENTITY e DEFAULT. Usa executeUpdate() e prepareStatement(sql, new String[]{"ID_CHECKIN"}), indicando explicitamente a coluna ao Oracle JDBC para getGeneratedKeys() retornar o ID (em vez de depender de ROWID). Não gera IDs na aplicação e não usa SELECT MAX. Na mesma conexão/transação, consulta data_checkin pelo ID recuperado, confirma com commit e só então devolve 201. Falhas SQL antes da confirmação tentam rollback; recursos JDBC usam try-with-resources.

Mantido Integer do model legado para idCheckin; o NUMBER da PK no banco pode exceder esse alcance futuramente. O comportamento real do driver/Oracle será validado manualmente pelo responsável pelo projeto.

### Segurança e escopo

Decisão de MVP: ainda não há JWT/sessão nem verificação de propriedade do beneficiário. O cliente escolhe o beneficiário pela URL. O login do Checkpoint 2 permanece independente. Não foram implementados score, medicação, dashboard, Telegram, cadastro ou alterações de frontend. Nenhum INSERT, UPDATE ou DELETE foi executado no Oracle real durante o desenvolvimento; os testes substituem ConnectionFactory e JDBC por mocks.

### Arquivos deste checkpoint

Criados em `src/main/java/br/fiap/daiji`: `controller/CheckinController.java`, `service/CheckinService.java`, `dto/CheckinRequest.java` e `dto/CheckinResponse.java`.

Criado: `src/test/java/br/fiap/daiji/CheckinApiTest.java`.

Alterados: `src/main/java/br/fiap/daiji/dao/CheckinDAO.java` (bean Spring e novo fluxo transacional) e `README.md`.

CheckinApiTest exercita Controller, Service e DAOs reais, simulando apenas JDBC: criação 201, parâmetros do INSERT e ID da URL apesar de campos conflitantes no JSON, campos opcionais, limites, estresse fracionário, IDs inválidos, 404 sem INSERT, SQL 500 sem detalhes, rollback e fechamento de recursos. BeneficiarioApiTest e AuthApiTest permanecem inalterados.

Validação em 16/09/2026: Maven verify com Java 21.0.10, BUILD SUCCESS. Total: 40 testes, 0 falhas, 0 erros, 0 ignorados (24 de check-in, 10 de login e 6 de beneficiário). JAR executável gerado. Oracle real não acessado. Sem commit ou push.

## Checkpoint 4 — cadastro e consulta de medicação

Fonte exclusiva do schema: `C:\Users\felip\.codex\attachments\25391e3f-f264-4922-9e0f-cc157273095d\pasted-text.txt`, script completo identificado como “DAIJI - Plataforma de Saude Preditiva Integrada / MVP Renal - Modelo de Banco de Dados (Oracle)”. O arquivo oficial foi somente lido. `scriptSql` é legado e não foi usado como autoridade.

### Estrutura oficial e relações

| Coluna de MEDICACAO | Definição |
| --- | --- |
| id_medicacao | NUMBER GENERATED ALWAYS AS IDENTITY; PK pk_medicacao |
| id_beneficiario | NUMBER NOT NULL; FK fk_medicacao_beneficiario → BENEFICIARIO(id_beneficiario) |
| nome_medicamento | VARCHAR2(100) NOT NULL |
| dosagem | VARCHAR2(50), opcional |
| horario_previsto | VARCHAR2(5), opcional |

Não há CHECK, UNIQUE adicional, status, datas, frequência ou defaults explícitos de dados nesta tabela. Horário é texto: o schema não exige HH:mm. O model Medicacao existente é compatível e foi preservado.

Existe CONFIRMACAO_MEDICACAO, com id_confirmacao IDENTITY (PK), id_medicacao obrigatório (FK para MEDICACAO), data_confirmacao TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, status_confirmacao VARCHAR2(15) DEFAULT 'PENDENTE' NOT NULL (CHECK: PENDENTE, CONFIRMADO, PERDIDO) e foto_url VARCHAR2(300) opcional. Essa estrutura não é necessária ao cadastro e fica para checkpoint posterior. Cadastrar medicação não confirma ingestão.

### Contrato REST

POST `/api/beneficiarios/7/medicacoes`

```json
{
  "nomeMedicamento": "Medicamento demonstrativo",
  "dosagem": "10 mg",
  "horarioPrevisto": "08:00"
}
```

Exemplo ilustrativo, sem recomendação clínica. Somente nomeMedicamento é obrigatório. Resposta HTTP 201 (ID ilustrativo gerado pelo banco):

```json
{
  "idMedicacao": 42,
  "idBeneficiario": 7,
  "nomeMedicamento": "Medicamento demonstrativo",
  "dosagem": "10 mg",
  "horarioPrevisto": "08:00"
}
```

GET `/api/beneficiarios/7/medicacoes`, HTTP 200:

```json
[
  {
    "idMedicacao": 42,
    "idBeneficiario": 7,
    "nomeMedicamento": "Medicamento demonstrativo",
    "dosagem": "10 mg",
    "horarioPrevisto": "08:00"
  }
]
```

Beneficiário existente sem medicações recebe 200 e `[]`. Ambos os endpoints verificam a existência via BeneficiarioDAO.buscarPorId e retornam 404 se ausente. A listagem filtra `WHERE id_beneficiario = ? ORDER BY id_medicacao`.

400: corpo ausente/malformado, nome ausente/vazio/em branco, limites excedidos ou identificador inválido. Nome, dosagem e horário limitados a 100/50/5 bytes UTF-8 respectivamente, seguindo a política conservadora do CP03. O SQL omite BYTE/CHAR, portanto a semântica efetiva depende do NLS do Oracle; a política pode rejeitar texto multibyte que caberia em um banco com semântica CHAR. Não há validação clínica nem formato obrigatório de horário. Strings opcionais vazias são convertidas em null, conforme Oracle.

Campos desconhecidos do JSON são ignorados conforme configuração atual, inclusive idBeneficiario/idMedicacao: associação vem exclusivamente da URL e ID vem exclusivamente do banco. 500 usa o tratamento genérico existente: `{"mensagem":"Não foi possível consultar o banco de dados."}`, sem detalhes JDBC/Oracle.

### Persistência e limites do MVP

MedicacaoController → MedicacaoService → MedicacaoDAO → ConnectionFactory → Oracle, com JDBC puro. INSERT usa executeUpdate e omite a PK; `prepareStatement(sql, new String[]{"ID_MEDICACAO"})` e getGeneratedKeys recuperam e validam a chave. Commit ocorre após inserção, recuperação da chave e fechamento dos recursos do statement. SQLException provoca tentativa de rollback; falha no rollback é anexada como suppressed. Conexão, statements e result sets fecham por try-with-resources. A conexão é encerrada, como no CP03, sem reutilização de estado transacional.

Integer foi preservado para IDs no Java, embora NUMBER Oracle permita valores maiores. Sem JWT/sessão ou autorização de propriedade: o cliente escolhe o beneficiário na URL. Login permanece independente. A compatibilidade real com Oracle ainda requer validação manual; os testes simulam apenas ConnectionFactory/JDBC. Não foram implementados confirmação de dose, score, Telegram, Gemini, frontend, atualização ou exclusão REST.

Criados: controller/MedicacaoController.java, service/MedicacaoService.java, dto/MedicacaoRequest.java e dto/MedicacaoResponse.java em src/main/java/br/fiap/daiji; src/test/java/br/fiap/daiji/MedicacaoApiTest.java.
Alterados: src/main/java/br/fiap/daiji/dao/MedicacaoDAO.java (bean Spring, criação transacional e listagem filtrada; métodos legados preservados) e README.md.

Validação final em 16/09/2026: mvn -o verify com Java 21.0.10, BUILD SUCCESS. 71 testes: 40 anteriores preservados (6 beneficiário, 10 login, 24 check-in) e 31 novos de medicação; zero falhas, erros ou ignorados. Log em target/cp04-verify.log. Oracle real não acessado; nenhum commit ou push.

## Checkpoint 5 — Score Daiji v1.0

Motor determinístico demonstrativo, histórico e consulta do score atual. Contratos, SQL oficial utilizado, regras e exemplos: [documentação CP05](docs/CP05-score.md).

## Checkpoint 6 — Gestão, dashboard e encaminhamentos

API de gestão com score atual, casos de atenção derivados e encaminhamentos por decisão humana. Endpoints, exemplos JSON, schema oficial conferido, testes e limitações: [documentação CP06](docs/CP06-gestao.md).

## Checkpoint 7 — Telegram

Bot local com Long Polling, comandos de consulta e transporte isolado para evolução futura a webhook. Configuração, arquitetura, exemplos, testes e limitação de autorização por ID: [documentação CP07](docs/CP07-telegram.md).

## Checkpoint 8 — Gemini

Linguagem natural com intenções validadas pelo Java e explicação restrita do score persistido, preservando os comandos CP07. Modelo, configuração, segurança, exemplos e testes: [documentação CP08](docs/CP08-gemini.md).

## Checkpoint 9 — Jornada do beneficiário

Check-in conversacional e confirmação de medicação compartilhada entre REST e Telegram. Schema oficial, contratos, fluxo, expiração, segurança e testes: [documentação CP09](docs/CP09-jornada-beneficiario.md).

## Checkpoint 10 — Cadastro de beneficiário

Cadastro público com empresa existente, transação atômica e BCrypt compatível com o login. Contratos, select de empresas, validações e testes: [documentação CP10](docs/CP10-cadastro-beneficiario.md).
