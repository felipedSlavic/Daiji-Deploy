# CP06 — Gestão, dashboard e encaminhamentos

## Objetivo

API para o Dashboard Web do MVP acadêmico DAIJI: consultar população e score atual, identificar casos de atenção, consultar profissionais e criar/atualizar encaminhamentos por decisão humana. Implementado diretamente em Daiji-Sprint04, branch main, sem commit/push. CP01–CP05 preservados.

## Fonte de verdade e estruturas reais

Script oficial completo: [pasted-text.txt](C:/Users/felip/.codex/attachments/25391e3f-f264-4922-9e0f-cc157273095d/pasted-text.txt), cabeçalho **DAIJI - Plataforma de Saude Preditiva Integrada / MVP Renal - Modelo de Banco de Dados (Oracle)**.

O scriptSql legado não foi usado como autoridade. Nenhum script oficial ou legado foi alterado ou executado. Não houve acesso Oracle real, alteração de schema, novas tabelas de produção, colunas ou índices.

| Tabela | Estrutura conferida |
| --- | --- |
| EMPRESA | id_empresa NUMBER GENERATED ALWAYS AS IDENTITY, PK pk_empresa; cnpj VARCHAR2(14) NOT NULL, UNIQUE uk_empresa_cnpj; razao_social VARCHAR2(150) NOT NULL; data_cadastro DATE DEFAULT SYSDATE NOT NULL. |
| BENEFICIARIO | id_beneficiario NUMBER GENERATED ALWAYS AS IDENTITY, PK pk_beneficiario; id_empresa NUMBER NOT NULL; nome VARCHAR2(150) NOT NULL; cpf VARCHAR2(11) NOT NULL, UNIQUE uk_beneficiario_cpf; email VARCHAR2(150) opcional, UNIQUE uk_beneficiario_email; data_nascimento DATE NOT NULL; telefone_whatsapp VARCHAR2(20) NOT NULL; status_ativo CHAR(1) DEFAULT 'S' NOT NULL, CHECK ck_beneficiario_status S/N; data_cadastro DATE DEFAULT SYSDATE NOT NULL. FK fk_beneficiario_empresa: id_empresa → EMPRESA(id_empresa). |
| PROFISSIONAL_SAUDE | id_profissional NUMBER GENERATED ALWAYS AS IDENTITY, PK pk_profissional_saude; nome VARCHAR2(150) NOT NULL; crm VARCHAR2(20) NOT NULL, UNIQUE uk_profissional_crm; especialidade VARCHAR2(50) NOT NULL. Não existe coluna de disponibilidade/agenda ou vínculo com empresa. |
| SCORE_RISCO_RENAL | id_score NUMBER GENERATED ALWAYS AS IDENTITY, PK pk_score_risco_renal; id_beneficiario NUMBER NOT NULL; data_calculo DATE NOT NULL; valor_score NUMBER(5,2) NOT NULL; classificacao_risco VARCHAR2(10) NOT NULL, CHECK ck_score_classificacao BAIXO/MEDIO/ALTO. FK fk_score_beneficiario: id_beneficiario → BENEFICIARIO(id_beneficiario). Histórico, sem unicidade por beneficiário. |
| ENCAMINHAMENTO | id_encaminhamento NUMBER GENERATED ALWAYS AS IDENTITY, PK pk_encaminhamento; id_beneficiario NUMBER NOT NULL; id_profissional NUMBER NOT NULL; data_encaminhamento DATE DEFAULT SYSDATE NOT NULL; data_consulta DATE opcional; status_encaminhamento VARCHAR2(15) DEFAULT 'PENDENTE' NOT NULL, CHECK ck_encaminhamento_status PENDENTE/AGENDADO/REALIZADO/CANCELADO. FKs fk_encaminhamento_beneficiario → BENEFICIARIO(id_beneficiario) e fk_encaminhamento_profissional → PROFISSIONAL_SAUDE(id_profissional). |

**Não existe ALERTA no script oficial; não foi criada tabela ou entidade ALERTA.** A fixture src/test/resources/cp06-fixture.sql reproduz somente as cinco tabelas para testes H2 em memória; não é migração ou script de produção.

## Arquitetura e score atual

- GestaoController → GestaoService → GestaoDAO/ProfissionalDAO → ConnectionFactory → Oracle.
- EncaminhamentoController → EncaminhamentoService → EncaminhamentoDAO, BeneficiarioDAO e ProfissionalDAO → ConnectionFactory → Oracle.
- EncaminhamentoService reutiliza GestaoService para validar empresa nos filtros.
- DTOs específicos; controllers delegam, services validam/orquestram, DAOs contêm SQL/mapeamento. JDBC puro, PreparedStatement, try-with-resources. Sem JPA, Hibernate ou JdbcTemplate.

CTE compartilhada:

~~~sql
ROW_NUMBER() OVER (
    PARTITION BY id_beneficiario
    ORDER BY data_calculo DESC, id_score DESC
) AS posicao
~~~

O LEFT JOIN exige posicao = 1 no ON. Cada beneficiário aparece uma única vez, inclusive sem score. O filtro ALTO é aplicado **depois** da seleção atual: um ALTO antigo seguido de BAIXO não aparece. O ID só desempata datas iguais; ID maior com data antiga não vence data mais recente.

O dashboard conta essa população e calcula ROUND(AVG(valor_score), 2). Quem não tem score conta no total e em semScore, mas não em classificações/média; média ausente é null. Encaminhamentos PENDENTE são contados em subconsulta separada para não multiplicar pessoas. O resumo é obtido em uma consulta SQL. Inativos continuam incluídos: não foi solicitado filtro statusAtivo.

Ordenação: ALTO, MEDIO, BAIXO, sem score; depois valorScore crescente, nome e idBeneficiario. Profissionais: nome e idProfissional. Encaminhamentos: dataEncaminhamento e idEncaminhamento decrescentes.

Nenhuma consulta chama ScoreService ou grava scores. Somente POST /api/beneficiarios/{id}/score/recalcular continua recalculando.

## Endpoints e exemplos JSON exatos

Exemplos fictícios. IDs e dataEncaminhamento gerados pelo banco variam em uso real. Campos null são serializados.

| Método/endpoint | Resposta | Filtros |
| --- | --- | --- |
| GET /api/gestao/dashboard | 200, resumo | idEmpresa opcional |
| GET /api/gestao/beneficiarios | 200, lista | idEmpresa opcional |
| GET /api/gestao/casos-atencao | 200, lista | idEmpresa opcional |
| GET /api/gestao/profissionais | 200, lista | Nenhum |
| POST /api/gestao/beneficiarios/{idBeneficiario}/encaminhamentos | 201, objeto | Body |
| GET /api/gestao/encaminhamentos | 200, lista | idEmpresa e status opcionais, combináveis |
| PATCH /api/gestao/encaminhamentos/{idEncaminhamento} | 200, objeto | Body |

Empresa ausente significa visão global; quando informada deve ser positiva e existir. Inexistente → 404. Vazia, não numérica, zero, negativa ou acima de Integer → 400. Empresa existente sem pessoas retorna resumo zerado/média null e listas vazias.

### GET /api/gestao/dashboard?idEmpresa=1

~~~json
{
  "idEmpresa": 1,
  "totalBeneficiarios": 4,
  "beneficiariosComScore": 3,
  "beneficiariosSemScore": 1,
  "baixoRisco": 1,
  "medioRisco": 1,
  "altoRisco": 1,
  "scoreMedio": 60.67,
  "encaminhamentosPendentes": 2
}
~~~

GET /api/gestao/dashboard no cenário dos testes:

~~~json
{
  "idEmpresa": null,
  "totalBeneficiarios": 7,
  "beneficiariosComScore": 5,
  "beneficiariosSemScore": 2,
  "baixoRisco": 2,
  "medioRisco": 1,
  "altoRisco": 2,
  "scoreMedio": 56.40,
  "encaminhamentosPendentes": 3
}
~~~

### GET /api/gestao/beneficiarios?idEmpresa=1

~~~json
[
  {
    "idBeneficiario": 2,
    "idEmpresa": 1,
    "nome": "Maria Silva",
    "email": "maria@daiji.com",
    "statusAtivo": "S",
    "valorScore": 25.00,
    "classificacaoRisco": "ALTO",
    "dataCalculoScore": "2026-09-16T12:30:45"
  },
  {
    "idBeneficiario": 3,
    "idEmpresa": 1,
    "nome": "Bruno",
    "email": null,
    "statusAtivo": "S",
    "valorScore": 60.00,
    "classificacaoRisco": "MEDIO",
    "dataCalculoScore": "2026-09-16T12:30:45"
  },
  {
    "idBeneficiario": 1,
    "idEmpresa": 1,
    "nome": "Rafael Almeida",
    "email": "rafael@daiji.com",
    "statusAtivo": "S",
    "valorScore": 97.00,
    "classificacaoRisco": "BAIXO",
    "dataCalculoScore": "2026-09-16T12:30:45"
  },
  {
    "idBeneficiario": 4,
    "idEmpresa": 1,
    "nome": "Sem score",
    "email": null,
    "statusAtivo": "S",
    "valorScore": null,
    "classificacaoRisco": null,
    "dataCalculoScore": null
  }
]
~~~

### GET /api/gestao/casos-atencao?idEmpresa=1

Reutiliza o DTO da listagem, incluindo email/statusAtivo. Sem casos: [].

~~~json
[
  {
    "idBeneficiario": 2,
    "idEmpresa": 1,
    "nome": "Maria Silva",
    "email": "maria@daiji.com",
    "statusAtivo": "S",
    "valorScore": 25.00,
    "classificacaoRisco": "ALTO",
    "dataCalculoScore": "2026-09-16T12:30:45"
  }
]
~~~

### GET /api/gestao/profissionais

Consulta cadastrados; não presume disponibilidade de agenda. Sem profissionais: [].

~~~json
[
  {"idProfissional": 2, "nome": "Dra. Ana Souza", "crm": "CRM-2", "especialidade": "Nefrologia"},
  {"idProfissional": 1, "nome": "Dra. Zelia", "crm": "CRM-1", "especialidade": "Nefrologia"}
]
~~~

### POST /api/gestao/beneficiarios/1/encaminhamentos

Request mínimo:

~~~json
{"idProfissional": 2}
~~~

Request com data opcional:

~~~json
{"idProfissional": 2, "dataConsulta": "2026-09-20T14:00:00"}
~~~

Response 201 para o request mínimo (dataEncaminhamento ilustrativa, fornecida pelo banco):

~~~json
{
  "idEncaminhamento": 7,
  "idBeneficiario": 1,
  "nomeBeneficiario": "Rafael Almeida",
  "idProfissional": 2,
  "nomeProfissional": "Dra. Ana Souza",
  "especialidade": "Nefrologia",
  "dataEncaminhamento": "2026-09-16T10:00:00",
  "dataConsulta": null,
  "statusEncaminhamento": "PENDENTE"
}
~~~

Beneficiário/profissional devem existir (404 se ausentes); idProfissional obrigatório e positivo. Permite qualquer score ou nenhum. Mesmo com dataConsulta, o status inicial é PENDENTE. INSERT omite ID, dataEncaminhamento e status, usando IDENTITY, SYSDATE e DEFAULT PENDENTE oficiais. JDBC solicita new String[]{"ID_ENCAMINHAMENTO"} e usa getGeneratedKeys; nunca SELECT MAX ou ID manual.

Inserção, recuperação/validação da chave e releitura ocorrem na mesma conexão/transação. Commit somente após sucesso. SQLException faz rollback, inclusive em falhas de releitura/commit. Falha do rollback é suppressed, nunca exposta no HTTP.

### GET /api/gestao/encaminhamentos?idEmpresa=1&status=AGENDADO

~~~json
[
  {
    "idEncaminhamento": 3,
    "idBeneficiario": 1,
    "nomeBeneficiario": "Rafael Almeida",
    "idProfissional": 2,
    "nomeProfissional": "Dra. Ana Souza",
    "especialidade": "Nefrologia",
    "dataEncaminhamento": "2026-09-16T10:00:00",
    "dataConsulta": "2026-09-20T14:00:00",
    "statusEncaminhamento": "AGENDADO"
  }
]
~~~

Status aceita exclusivamente PENDENTE, AGENDADO, REALIZADO, CANCELADO, nessa caixa e sem espaços. Desconhecido/vazio → 400. Sem filtro lista todos; sem resultados retorna [].

### PATCH /api/gestao/encaminhamentos/1

Request:

~~~json
{"statusEncaminhamento": "AGENDADO", "dataConsulta": "2026-09-20T14:00:00"}
~~~

Response 200:

~~~json
{
  "idEncaminhamento": 1,
  "idBeneficiario": 2,
  "nomeBeneficiario": "Maria Silva",
  "idProfissional": 2,
  "nomeProfissional": "Dra. Ana Souza",
  "especialidade": "Nefrologia",
  "dataEncaminhamento": "2026-09-16T10:00:00",
  "dataConsulta": "2026-09-20T14:00:00",
  "statusEncaminhamento": "AGENDADO"
}
~~~

Status obrigatório, limitado aos quatro valores. AGENDADO exige dataConsulta no request. PENDENTE/REALIZADO/CANCELADO permitem null. **Data omitida ou explicitamente null limpa o campo; para preservar uma data existente, envie-a novamente.** Beneficiário/profissional/dataEncaminhamento não mudam. Não existe máquina de transições adicionais: a gestão pode escolher qualquer um dos quatro status.

UPDATE verifica linhas afetadas: zero → 404 sem commit; uma linha → releitura na mesma transação antes do commit; falhas → rollback. Sem diagnósticos, observações ou campos clínicos adicionais.

### Datas e erros HTTP

Oracle DATE também armazena hora/minuto/segundo. LocalDateTime ISO-8601 sem offset nos DTOs; setTimestamp/getTimestamp no JDBC preservam horário. Frações são truncadas antes da escrita. Anos aceitos 1–9999; datas inválidas → 400. Sem regra de data futura/passada.

Mantido ApiExceptionHandler existente. Consultas/updates 200; criação 201; parâmetros/body/status inválidos 400; recursos inexistentes 404; SQLException 500:

~~~json
{"mensagem":"Não foi possível consultar o banco de dados."}
~~~

Sem ORA, SQL, URL JDBC, usuário, senha ou stack trace na resposta. 400/404 seguem a configuração Spring existente; não foi introduzido novo formato de body.

## Fluxo humano e limitações

Score atual ALTO → caso de atenção no dashboard → gestão decide → escolhe profissional → POST cria encaminhamento → PATCH informa status/data.

ALTO é derivado dinamicamente, sem ALERTA persistido, notificações ou encaminhamento automático. Score não é diagnóstico ou recomendação clínica.

Limitações: sem JWT/roles/autorização de gestor; idEmpresa é filtro, não controle de acesso; sem paginação; sem disponibilidade de agenda; sem criação de profissionais; sem regras clínicas de transição; sem deduplicação de encaminhamentos; sem controle otimista de concorrência (última atualização vence). IDs de empresa/beneficiário/profissional seguem Integer dos CPs anteriores; idEncaminhamento usa Long. NUMBER Oracle comporta valores maiores. Sem frontend, IA/ML, Gemini, mensageria, Node.js ou Python.

Compatibilidade Oracle orientada pelo script oficial e JDBC. A validação manual do driver/banco real fica para etapa posterior, conforme pedido de não acessar Oracle real nesta execução.

## Testes

Java 21.0.10 e Maven instalado no IntelliJ. PATH padrão contém Java 8 e não contém mvn. Comando equivalente executado no PowerShell:

~~~powershell
$env:JAVA_HOME='C:\Users\felip\.jdks\ms-21.0.10'
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1\plugins\maven\lib\maven3\bin\mvn.cmd' verify
~~~

Baseline: 141 testes, BUILD SUCCESS, zero falhas/erros/ignorados, log target/cp06-baseline.log.

GestaoApiTest usa MockMvc, controllers/services/DAOs reais e ConnectionFactory substituída por Mockito para retornar somente H2 em memória, MODE=Oracle. Executa as consultas SQL e testa resultados/persistência: histórico, desempate, média atual, população com/sem score, ordenação, empresa, casos de atenção, profissionais, filtros, criação, defaults, atualização, datas/IDs/status, HTTP e ausência de escrita pelas leituras.

EncaminhamentoDAOTest usa JDBC simulado para validar IDENTITY, parâmetros, horário, conexão única, ordem de commit/releitura/fechamento e rollback em falhas de INSERT, chave, UPDATE, leitura, contagem de linhas e commit.

H2 é dependência somente test, não é empacotado como dependência da aplicação. Não substitui uma validação Oracle real. Os seis arquivos de testes anteriores permanecem inalterados; nenhum teste abre Oracle.

## Arquivos

Criados, relativos à raiz Daiji-Sprint04:

~~~text
docs/CP06-gestao.md
src/main/java/br/fiap/daiji/controller/GestaoController.java
src/main/java/br/fiap/daiji/controller/EncaminhamentoController.java
src/main/java/br/fiap/daiji/service/GestaoService.java
src/main/java/br/fiap/daiji/service/EncaminhamentoService.java
src/main/java/br/fiap/daiji/dao/GestaoDAO.java
src/main/java/br/fiap/daiji/dao/ProfissionalDAO.java
src/main/java/br/fiap/daiji/dao/EncaminhamentoDAO.java
src/main/java/br/fiap/daiji/dto/DashboardResponse.java
src/main/java/br/fiap/daiji/dto/GestaoBeneficiarioResponse.java
src/main/java/br/fiap/daiji/dto/ProfissionalResponse.java
src/main/java/br/fiap/daiji/dto/EncaminhamentoRequest.java
src/main/java/br/fiap/daiji/dto/EncaminhamentoUpdateRequest.java
src/main/java/br/fiap/daiji/dto/EncaminhamentoResponse.java
src/test/java/br/fiap/daiji/GestaoApiTest.java
src/test/java/br/fiap/daiji/EncaminhamentoDAOTest.java
src/test/resources/cp06-fixture.sql
~~~

Alterados: pom.xml (H2 somente test) e README.md (referência CP06). Nenhum arquivo de implementação CP01–CP05 alterado. Logs/relatórios locais em target não versionados.

## Resultado final de verificação — 16/09/2026

**mvn verify: BUILD SUCCESS — 238 testes, 0 falhas, 0 erros, 0 ignorados.**

| Suíte | Testes |
| --- | ---: |
| AuthApiTest (anterior) | 10 |
| BeneficiarioApiTest (anterior) | 6 |
| CheckinApiTest (anterior) | 24 |
| MedicacaoApiTest (anterior) | 31 |
| ScoreApiTest (anterior) | 22 |
| ScoreServiceTest (anterior) | 48 |
| GestaoApiTest (CP06) | 76 |
| EncaminhamentoDAOTest (CP06) | 21 |
| Total | 238 |

**141 testes anteriores preservados e passando + 97 novos.** Log final: target/cp06-verify.log; relatórios: target/surefire-reports. Oracle real não acessado. Script/schema intactos. Nenhuma tabela ALERTA. Sem commit/push.
