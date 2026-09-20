# CP05 — Score Daiji v1.0

Score demonstrativo do MVP acadêmico. Não é um escore clínico validado, não diagnostica doença renal e não recomenda tratamento. O cálculo é exclusivamente Java, determinístico para os mesmos dados e data de referência. Não há integração com Gemini, alertas ou encaminhamentos.

## Fonte de verdade e estrutura conferida

Script oficial completo utilizado, sem alteração:
`C:\Users\felip\.codex\attachments\25391e3f-f264-4922-9e0f-cc157273095d\pasted-text.txt`

Cabeçalho: **DAIJI - Plataforma de Saude Preditiva Integrada / MVP Renal - Modelo de Banco de Dados (Oracle)**. O `scriptSql` na raiz é legado e não foi usado como autoridade. Nenhum script foi executado, copiado sobre o legado ou alterado.

| Tabela | Estrutura relevante e constraints conferidas |
| --- | --- |
| BENEFICIARIO | `id_beneficiario NUMBER GENERATED ALWAYS AS IDENTITY` PK; `data_nascimento DATE NOT NULL`; FK `id_empresa` → EMPRESA; CPF/e-mail únicos; status S/N |
| COMORBIDADE | `id_comorbidade NUMBER GENERATED ALWAYS AS IDENTITY` PK; `descricao VARCHAR2(100) NOT NULL UNIQUE` |
| BENEFICIARIO_COMORBIDADE | PK composta (`id_beneficiario`, `id_comorbidade`); duas FKs para BENEFICIARIO e COMORBIDADE; `data_diagnostico DATE NOT NULL` |
| CHECKIN | IDENTITY PK; FK para BENEFICIARIO; `data_checkin TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL`; `nivel_estresse NUMBER(1)` entre 1–5; sono VARCHAR2(20), alimentação VARCHAR2(30), humor VARCHAR2(20), todos opcionais; canal WHATSAPP/TELEGRAM/APP/SMS |
| MEDICACAO | IDENTITY PK; `id_beneficiario NUMBER NOT NULL` FK para BENEFICIARIO; medicamento VARCHAR2(100) obrigatório; dosagem VARCHAR2(50), horário VARCHAR2(5) opcionais |
| CONFIRMACAO_MEDICACAO | IDENTITY PK; `id_medicacao NUMBER NOT NULL` FK para MEDICACAO; data TIMESTAMP; `status_confirmacao VARCHAR2(15) DEFAULT 'PENDENTE' NOT NULL`, CHECK PENDENTE/CONFIRMADO/PERDIDO |
| SCORE_RISCO_RENAL | `id_score NUMBER GENERATED ALWAYS AS IDENTITY` PK; `id_beneficiario NUMBER NOT NULL` FK para BENEFICIARIO; `data_calculo DATE NOT NULL`; `valor_score NUMBER(5,2) NOT NULL`; `classificacao_risco VARCHAR2(10) NOT NULL`, CHECK BAIXO/MEDIO/ALTO |

Oracle DATE preserva data e hora até segundos. O serviço usa America/Sao_Paulo e trunca frações de segundo; JDBC usa `setTimestamp/getTimestamp`, e o DTO usa LocalDateTime (sem offset, coerente com a coluna). Um Clock fixo torna testes de idade/data independentes do calendário real.

## Consultas e cálculo

`ScoreController → ScoreService → BeneficiarioDAO/ScoreDAO → ConnectionFactory → Oracle`.

1. BeneficiarioDAO verifica existência e fornece data de nascimento; idade calculada por `Period.between(nascimento, dataCalculo).getYears()`, nunca persistida.
2. ScoreDAO consulta descrições de COMORBIDADE por JOIN com BENEFICIARIO_COMORBIDADE, filtrado pelo beneficiário. Java normaliza espaços externos, espaços internos repetidos, caixa com Locale.ROOT e acentos; deduplica descrições normalizadas.
3. Reconhecimento por lista explícita, sem substring nem inferência clínica: hipertensão, hipertensão arterial, hipertensão arterial sistêmica, HAS; diabetes, diabetes mellitus, diabetes tipo 1/2, diabetes mellitus tipo 1/2, DM, DM1, DM2. Cada categoria principal desconta uma vez e seus aliases são excluídos das demais. Demais descrições distintas descontam 5 cada, até 15.
4. Adesão: JOIN CONFIRMACAO_MEDICACAO → MEDICACAO, filtro pelo beneficiário e apenas CONFIRMADO/PERDIDO; COUNT agrupado por status, sobre todos os registros existentes. PENDENTE não participa. Não há doses inferidas nem confirmações geradas.
5. Check-ins: apenas quatro campos estruturados; `ORDER BY data_checkin DESC, id_checkin DESC FETCH FIRST 7 ROWS ONLY`. O desempate pelo ID torna a seleção estável. Não se consulta resposta_texto. O limite é aplicado antes de ignorar componentes desconhecidos; não se buscam registros antigos para substituí-los.
6. ScoreDAO insere exclusivamente as quatro colunas não IDENTITY; recupera `ID_SCORE` com `prepareStatement(sql, new String[]{"ID_SCORE"})` e `getGeneratedKeys()`. Confere uma linha afetada e chave positiva; commit após recuperar a chave, rollback em SQLException. Nunca usa SELECT MAX.
7. GET consulta `ORDER BY data_calculo DESC, id_score DESC FETCH FIRST 1 ROW ONLY`; não consulta fatores, não recalcula nem grava.

```text
score = clamp(100 - hipertensao - diabetes - outrasComorbidades
                  - idade - adesaoMedicacao - checkins, 0, 100)
```

| Categoria | Penalidade positiva subtraída |
| --- | --- |
| Hipertensão / diabetes | 10 por categoria, no máximo uma vez |
| Outras comorbidades | min(quantidade distinta, 3) × 5 |
| Idade | <40: 0; 40–59: 3; 60–69: 5; ≥70: 10 |
| Adesão | ≥90%: 0; ≥75%: 5; ≥50%: 10; <50%: 15 |
| Check-ins | índice ≤0,25: 0; ≤0,50: 5; ≤0,75: 10; >0,75: 15 |

Adesão = confirmados / (confirmados + perdidos) × 100. Comparações por multiplicação de BigDecimal evitam arredondamento de percentuais nas fronteiras.

Check-in: estresse 1/2 → 0, 3 → 1, 4 → 2, 5 → 3 (máximo 3); sono/alimentação BOA/BOM → 0, REGULAR → 1, RUIM → 2 (máximo 2 cada); humor CALMO/BEM/BOM/FELIZ/POSITIVO → 0, REGULAR/NEUTRO → 1, TRISTE/ANSIOSO/ESTRESSADO/IRRITADO/RUIM/NEGATIVO → 2 (máximo 2). Texto normalizado com a mesma função. Null/desconhecido ignora tanto pontos quanto máximo desse componente.

Índice = soma dos pontos / soma dos máximos reconhecidos dos últimos sete registros. Comparações inteiras equivalentes preservam as fronteiras sem arredondamento; não se faz média de percentuais individuais.

Denominador zero ou nenhum componente reconhecido: penalidade zero por dados insuficientes. Os fatores incluem `dadosAdesaoSuficientes` e `dadosCheckinsSuficientes` para explicitar isso. Ausência de comorbidade também não penaliza. O máximo de descontos v1 é 75, portanto a faixa efetivamente alcançável é 25–100; o clamp continua garantindo 0–100.

Classificação: 70–100 BAIXO; 40–<70 MEDIO; 0–<40 ALTO. Quanto maior o Score Daiji, melhor a situação; quanto menor, maior o risco. ALTO apenas é persistido/devolvido. Testes explícitos cobrem 100, 70, o double imediatamente abaixo de 70 (`Math.nextDown`), 40, o double imediatamente abaixo de 40 e 0.

## Contrato HTTP e exemplos exatos

POST `/api/beneficiarios/7/score/recalcular`, sem body, **201 Created** porque cada chamada cria uma nova linha de histórico. Repetir com os mesmos dados/data mantém valor e fatores, mas cria novo ID/momento. Exemplo fictício validado integralmente no teste de API (Clock fixo, nascimento 1980-01-02, hipertensão, uma outra comorbidade, confirmação e check-in favoráveis):

```json
{
  "idScore": 42,
  "idBeneficiario": 7,
  "dataCalculo": "2026-09-16T12:30:45",
  "valorScore": 82.00,
  "classificacaoRisco": "BAIXO",
  "fatores": {
    "hipertensao": 10,
    "diabetes": 0,
    "outrasComorbidades": 5,
    "idade": 3,
    "adesaoMedicacao": 0,
    "checkins": 0,
    "dadosAdesaoSuficientes": true,
    "dadosCheckinsSuficientes": true
  }
}
```

GET `/api/beneficiarios/7/score`, **200 OK**. Exemplo fictício validado integralmente no teste de API:

```json
{
  "idScore": 55,
  "idBeneficiario": 7,
  "dataCalculo": "2026-09-16T12:30:45",
  "valorScore": 82.00,
  "classificacaoRisco": "BAIXO"
}
```

Fatores não são persistidos e são omitidos no GET: recalculá-los com os dados de hoje não explicaria necessariamente o histórico armazenado.

ID inválido: 400. Beneficiário inexistente: 404. GET sem histórico: 404 (`Score não encontrado.` no motivo da exceção, seguindo ResponseStatusException já usado no projeto). SQLException: 500 com corpo genérico pelo ApiExceptionHandler existente:

```json
{"mensagem":"Não foi possível consultar o banco de dados."}
```

## Arquivos e validação

Criados:
- src/main/java/br/fiap/daiji/controller/ScoreController.java
- src/main/java/br/fiap/daiji/service/ScoreService.java
- src/main/java/br/fiap/daiji/dao/ScoreDAO.java
- src/main/java/br/fiap/daiji/dto/ScoreResponse.java
- src/main/java/br/fiap/daiji/model/ScoreDados.java
- src/test/java/br/fiap/daiji/ScoreServiceTest.java
- src/test/java/br/fiap/daiji/ScoreApiTest.java
- docs/CP05-score.md

README.md recebe um link para esta documentação. Nenhum arquivo dos checkpoints anteriores, dependência, schema ou script SQL foi alterado. Testes usam Mockito para ConnectionFactory e objetos JDBC; não abrem conexão Oracle real. Testes de API atravessam Controller, Service e DAO até JDBC simulado; não substituem validação manual futura no Oracle.

Validação final: `mvn verify` com JDK 21.0.10, **BUILD SUCCESS**, 141 testes, 0 falhas, 0 erros, 0 ignorados. CP01–CP04 preservados: Auth 10 + Beneficiario 6 + Checkin 24 + Medicacao 31 = **71 anteriores**. CP05: ScoreService 48 + ScoreApi 22 = **70 novos**. Log local em `target/cp05-verify.log`; relatórios em `target/surefire-reports`. Sem acesso Oracle real, sem commit e sem push; trabalho diretamente na branch `main`.

SHA-256 do script oficial consultado: `67B2179A9B46F540B4DA4F70C93C9C9A61C67F21BC6ED89ED24250E84FD77FC5`.
