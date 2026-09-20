# CP09 — Jornada do beneficiário

Check-in conversacional e registro de confirmação de medicação no backend Java 21/Spring Boot/JDBC puro. REST e Telegram chamam os mesmos casos de uso. Sem alteração do schema, frontend, deploy, webhook, fotos, OCR ou Gemini Vision.

## Fonte e estrutura oficial

Fonte lida: script completo do MVP final em `C:/Users/felip/.codex/attachments/25391e3f-f264-4922-9e0f-cc157273095d/pasted-text.txt`. O mini-script legado não foi usado. A leitura do anexo não acessa o Oracle real.

Estrutura exata encontrada em `CONFIRMACAO_MEDICACAO`:

| Coluna | Tipo e regra |
| --- | --- |
| `id_confirmacao` | `NUMBER GENERATED ALWAYS AS IDENTITY` |
| `id_medicacao` | `NUMBER NOT NULL` |
| `data_confirmacao` | `TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL` |
| `status_confirmacao` | `VARCHAR2(15) DEFAULT 'PENDENTE' NOT NULL` |
| `foto_url` | `VARCHAR2(300)`, opcional |

Constraints oficiais:

- `pk_confirmacao_medicacao`: PK de `id_confirmacao`.
- `ck_confirmacao_status`: somente `PENDENTE`, `CONFIRMADO`, `PERDIDO`.
- `fk_confirmacao_medicacao`: `id_medicacao` referencia `MEDICACAO(id_medicacao)`.
- `MEDICACAO.id_beneficiario` é obrigatório; `fk_medicacao_beneficiario` referencia `BENEFICIARIO(id_beneficiario)`.

Não há coluna de beneficiário diretamente na confirmação. A propriedade é obtida pela medicação. Não há UNIQUE adicional que proíba múltiplas confirmações por medicação/data: registros repetidos são permitidos e podem representar horários/dias diferentes. O CP09 não inventa deduplicação de domínio nem idempotência permanente.

O schema oficial de `CHECKIN` aceita `WHATSAPP`, **`TELEGRAM`**, `APP` e `SMS` em `ck_checkin_canal`. Por isso, Telegram grava `TELEGRAM`; o POST REST CP03 continua gravando `APP`. Não foi necessário alterar constraint nem usar um canal substituto.

Sono, alimentação e humor não têm enum/CHECK no schema ou CP03: são texto livre opcional, com limites respectivos de 20, 30 e 20 bytes UTF-8 na validação Java existente. Estresse opcional aceita 1–5. `resposta_texto` é opcional, limitado a 500 bytes UTF-8 pelo CP03.

## Arquitetura

```text
REST → ConfirmacaoMedicacaoController → ConfirmacaoMedicacaoService
                                     → ConfirmacaoMedicacaoDAO → Oracle

TelegramUpdateReceiver → BeneficiaryJourney
                         ├─ fluxo ativo → CheckinConversations → CheckinService → CheckinDAO
                         ├─ /checkin → CheckinConversations
                         ├─ /confirmar → ConfirmacaoMedicacaoService → DAO
                         ├─ escrita natural → JourneyNaturalLanguage → Gemini → validação Java
                         └─ consultas anteriores → MessageRouter CP07/CP08
```

Não há SQL em classes Telegram/assistant. `CheckinService` compartilha validação e persistência entre REST e conversa; a sobrecarga com `CanalCheckin` recebe um canal definido pelo código, não pelo corpo HTTP. A assinatura REST anterior continua intacta. O início também verifica o beneficiário pelo mesmo Service.

`ConfirmacaoMedicacaoService` valida IDs, status e existência do beneficiário. O DAO consulta `MEDICACAO` usando **ambos os IDs** e `FOR UPDATE` na mesma conexão/transação da escrita. A linha fica bloqueada até commit/rollback, evitando que a propriedade seja alterada entre a verificação e o INSERT. Ausência ou medicação de outra pessoa resulta em 404, sem INSERT.

Gemini não conhece DAO, Connection ou SQL. As novas intenções usam contrato separado para preservar integralmente as sete intenções de consulta do CP08. Polling, offset e shutdown CP07 não foram alterados. Um webhook futuro pode chamar o mesmo receiver e a mesma jornada.

## Contrato REST

```text
POST /api/beneficiarios/{idBeneficiario}/medicacoes/{idMedicacao}/confirmacoes
Content-Type: application/json
```

Exemplo exato de requisição:

```text
POST /api/beneficiarios/1/medicacoes/2/confirmacoes
```

```json
{
  "statusConfirmacao": "CONFIRMADO"
}
```

Exemplo exato do formato da resposta 201, com ID/data ilustrativos gerados pelo banco:

```json
{
  "idConfirmacao": 42,
  "idBeneficiario": 1,
  "idMedicacao": 2,
  "dataConfirmacao": "2026-09-17T10:30:45",
  "statusConfirmacao": "CONFIRMADO"
}
```

O body exige `statusConfirmacao` exatamente em maiúsculas, sem espaços adicionais, com um dos três valores oficiais. Embora o banco tenha default `PENDENTE`, a operação pede status explícito. IDs positivos são recebidos apenas da URL e seguem o alcance Integer existente. A confirmação usa long para sua chave gerada.

Campos adicionais são rejeitados com 400, incluindo `idBeneficiario`, `idMedicacao`, ID da confirmação, data e foto. Não se aceita divergência entre URL e JSON. `foto_url` permanece null e não integra o contrato CP09.

| HTTP | Situação |
| --- | --- |
| 201 | Confirmação criada e transação confirmada |
| 400 | ID inválido, body ausente/malformado, status ausente/inválido ou campo extra |
| 404 | Beneficiário inexistente; medicação inexistente ou pertencente a outro beneficiário |
| 500 | Falha JDBC, com o tratamento genérico existente |

Resposta exata de falha JDBC:

```json
{"mensagem":"Não foi possível consultar o banco de dados."}
```

Não expõe ORA, SQL, URL JDBC, credenciais ou stack trace. Para 400/404, o cliente deve usar o status HTTP; não foi introduzido um novo envelope de erros nos contratos anteriores.

## Persistência da confirmação

1. Service valida entrada e busca beneficiário.
2. DAO abre ConnectionFactory, desativa autocommit e valida propriedade com SELECT parametrizado e bloqueio da medicação.
3. INSERT fornece somente `id_medicacao` e `status_confirmacao`; omite ID, data e foto.
4. `prepareStatement(..., new String[]{"ID_CONFIRMACAO"})` e `getGeneratedKeys()` recuperam a chave IDENTITY, sem geração manual ou SELECT MAX.
5. Na mesma transação, SELECT por ID recupera data/status realmente persistidos.
6. Commit precede a resposta de sucesso. SQLException provoca rollback; falha de rollback é preservada como suppressed, sem exposição ao usuário. Connection, statements e result sets são fechados por try-with-resources.

Ausência de propriedade também provoca rollback para liberar a transação, sem gravação. Repetir uma requisição completa pode criar outra confirmação, pois o schema permite múltiplos registros. Não repetir automaticamente diante de timeout/resultado incerto de commit.

## Check-in conversacional

Comando exato:

```text
/checkin <idBeneficiario>
```

Exemplo: `/checkin 1`. Aceita caixa do comando, espaços extras e sufixo `@NomeDoBot`. O ID segue a validação CP03: 1–999999999. Beneficiário é verificado antes da criação do estado.

Máquina de estados Java:

```text
STRESS → SLEEP → FOOD → MOOD → NOTE → CONFIRM → gravação/remoção
             cancelamento ou expiração → remoção sem gravação
```

| Etapa | Entrada e comportamento |
| --- | --- |
| Estresse | Somente um dígito de `1` a `5`. Inválido: `Informe um número de 1 a 5.`; não avança |
| Sono | Texto não vazio até 20 bytes UTF-8, normalizado para maiúsculas. Sugestões: RUIM, REGULAR, BOA |
| Alimentação | Texto não vazio até 30 bytes UTF-8, normalizado para maiúsculas. Sugestões: RUIM, REGULAR, BOA |
| Humor | Texto não vazio até 20 bytes UTF-8, normalizado para maiúsculas. Sugestões: CALMO, REGULAR, TRISTE |
| Observação | Texto até 500 bytes UTF-8 ou `PULAR`, que produz null. Não interpreta instruções presentes no texto |
| Confirmação | Mostra resumo; somente `CONFIRMAR` registra. `CANCELAR` descarta |

As sugestões não formam um enum novo: outros textos aceitos pelo CP03 também são aceitos. Inputs vazios/longos são rejeitados mantendo a etapa. Limites são verificados por `CheckinService.validarRequest`, a mesma validação usada na criação. O fluxo coleta respostas para as primeiras quatro etapas, mas o contrato REST continua permitindo campos opcionais como antes.

Exemplo completo de conversa:

```text
Usuário: /checkin 1
Bot: Vamos realizar o check-in.

De 1 a 5, qual é seu nível de estresse hoje?

Usuário: 3
Bot: Como foi a qualidade do seu sono?
Sugestões: RUIM, REGULAR ou BOA. Pode informar outro texto de até 20 bytes UTF-8.

Usuário: boa
Bot: Como foi a qualidade da sua alimentação?
Sugestões: RUIM, REGULAR ou BOA. Pode informar outro texto de até 30 bytes UTF-8.

Usuário: boa
Bot: Como está seu humor?
Sugestões: CALMO, REGULAR ou TRISTE. Pode informar outro texto de até 20 bytes UTF-8.

Usuário: calmo
Bot: Deseja adicionar uma observação sobre o dia?
Envie o texto ou digite PULAR.

Usuário: Dia tranquilo
Bot: Confira seu check-in:

Estresse: 3/5
Sono: BOA
Alimentação: BOA
Humor: CALMO
Observação: Dia tranquilo

Digite CONFIRMAR para registrar
ou CANCELAR para descartar.

Usuário: CONFIRMAR
Bot: Check-in registrado com sucesso. ✅
```

Antes da confirmação não há INSERT. Após confirmar, chama `CheckinService.criar(id, request, CanalCheckin.TELEGRAM)`; data e ID continuam vindo do DAO/banco existente. O estado é removido imediatamente após a tentativa. Falha não afirma sucesso e também encerra o fluxo para evitar reenvio automático de uma escrita cujo commit possa ter resultado incerto:

```text
Não foi possível confirmar o registro agora. O fluxo foi encerrado. Consulte /checkins <idBeneficiario> antes de iniciar outro check-in.
```

### Cancelamento, concorrência e expiração

`CANCELAR` ou `/cancelar` durante qualquer etapa removem o estado e respondem `Check-in cancelado.`. Sem fluxo, `/cancelar` responde `Não há check-in em andamento.`. Outros comandos slash são bloqueados enquanto há fluxo ativo, sem destruir respostas, com orientação para responder à etapa ou usar `/cancelar`. Um novo `/checkin` não substitui silenciosamente o anterior.

Estado em `ConcurrentHashMap<Long, State>`, com chatId somente como chave local. Guarda ID do beneficiário, etapa, respostas necessárias e instante da última interação. Operações usam `compute` para serializar transições do mesmo chat, inclusive a confirmação; chats diferentes têm estados distintos. Duas confirmações concorrentes da mesma conversa não geram dois INSERTs de check-in.

Expiração: 15 minutos de inatividade, com Clock injetável nos testes. Toda mensagem recebida verifica expiração antes de avançar. Interações válidas ou inválidas no fluxo renovam o prazo. Um único executor daemon limpa estados abandonados a cada minuto; logo, a remoção física sem novas mensagens ocorre normalmente entre 15 e 16 minutos. Não depende da chegada de outra mensagem para eliminar dados abandonados. O executor é encerrado no shutdown e os estados são descartados.

Se a própria mensagem encontra estado expirado, informa a expiração e pede novo `/checkin`. Se a limpeza já removeu o estado, as próximas mensagens seguem o roteamento normal, sem contexto anterior. Reiniciar a aplicação perde todos os fluxos em andamento. Não há armazenamento de chatId, sessões ou respostas parciais no Oracle.

Enquanto o estado está ativo, texto passa exclusivamente pela máquina de estados Java e nunca pelo Gemini. A observação é dado livre para persistência, não instrução clínica ou comando executável.

## Confirmação de medicação pelo Telegram

Comando exato:

```text
/confirmar <idBeneficiario> <idMedicacao> PENDENTE|CONFIRMADO|PERDIDO
```

Exemplo:

```text
/confirmar 1 2 CONFIRMADO
```

O comando completamente informado chama o mesmo Service REST após validação. Status é normalizado para maiúsculas no canal Telegram. Sucesso: `Confirmação registrada com sucesso.`. Não recomenda uso, dose, horário ou alteração de tratamento.

Os comandos anteriores são preservados. Para descobrir IDs sem mudar a resposta CP07 de `/medicacoes`, foi acrescentado:

```text
/medicacoes_ids <idBeneficiario>
```

Esse comando usa `MedicacaoService.listar` e o mesmo filtro SQL por beneficiário. Mostra ID, nome, dose e horário já cadastrados; lista vazia retorna a mensagem anterior. `/jornada` mostra os comandos novos. `/ajuda` preserva o contrato CP07.

## Linguagem natural e Gemini

Com Gemini habilitado/configurado, são suportadas formas explícitas como:

- `Quero fazer o check-in do beneficiário 1`
- `Fazer check-in do beneficiário 1`
- `Quero registrar o check-in do ID 1`
- `O beneficiário 1 tomou a medicação 2`
- `Confirmar a medicação 2 do beneficiário 1 com status PERDIDO`
- `Confirmar medicação 2 do ID 1 como PENDENTE`

As intenções novas são `REALIZAR_CHECKIN` e `CONFIRMAR_MEDICACAO`, com `FORA_DE_ESCOPO` como rejeição. Elas têm JSON Schema próprio com quatro campos obrigatórios: `intent`, `idBeneficiario`, `idMedicacao`, `statusConfirmacao`. O contrato de consulta/explicação CP08 permanece inalterado.

Java primeiro exige uma forma textual reconhecível, completa e sem ambiguidades. Depois Gemini interpreta a mensagem. Java valida JSON estrito, campos, tipos, intenção, IDs e status; exige coincidência exata com os dados explícitos que conseguiu verificar localmente. O modelo não pode trocar beneficiário, medicação, status nem operação. O verbo afirmativo no passado `tomou` equivale somente ao registro declarativo `CONFIRMADO`; não é prova de ingestão.

Perguntas, condicionais, negações, falta de IDs, status desconhecido ou informação incompleta não persistem. `Confirmar a medicação 2 do beneficiário 1` sem status pede esclarecimento. `Tomei meu remédio` responde:

```text
Informe o ID do beneficiário e da medicação.
Use /medicacoes_ids <idBeneficiario> para consultar os IDs das medicações cadastradas.
```

Texto fora das formas suportadas pode pedir esclarecimento: este MVP não tenta compreender livremente todas as maneiras de solicitar uma escrita. A triagem de privacidade CP08 continua aplicada e pode rejeitar entradas conservadoramente. Falha/disabled do Gemini nunca impede `/checkin` e `/confirmar`. Nenhuma resposta do questionário ativo é enviada ao provedor.

## Score, segurança e limites

Uma nova confirmação poderá influenciar um recálculo posterior do Score Daiji, conforme a regra determinística do CP05. `ScoreDAO.adesao` já lê `CONFIRMACAO_MEDICACAO` via MEDICACAO e contabiliza CONFIRMADO/PERDIDO, ignorando PENDENTE. Fórmula, pesos e histórico do score não foram alterados. Não há recálculo automático após check-in ou confirmação.

Confirmar significa registrar o relato do usuário. Não certifica ingestão, adesão perfeita ou resultado clínico. O bot não prescreve, recomenda horários/doses, substitui medicamento, diagnostica ou instrui interrupção.

Continua inexistente a autenticação/autorização Telegram por beneficiário. Quem conhece IDs pode consultar/interagir com aquele cadastro; o vínculo de propriedade medicação–beneficiário impede mistura entre os IDs informados, mas não comprova a identidade do usuário. Utilizar dados demonstrativos e público controlado.

Estado local não guarda CPF, email, telefone, senhas, tokens ou dados cadastrais. Observação é texto fornecido pelo usuário: não envie dados sensíveis desnecessários. O fluxo não registra conteúdos em logs nem os envia ao Gemini. Não há vínculo permanente chat–beneficiário.

Foto está fora do escopo, apesar da coluna `foto_url` existente: não há getFile, download, upload, OCR, reconhecimento de medicamentos, multimodalidade ou Gemini Vision.

## REST disponível ao frontend

Login, score, criação de check-in, listagem de medicações, gestão/dashboard e encaminhamentos anteriores permanecem disponíveis. O frontend pode chamar o novo POST de confirmações diretamente, sem depender do Telegram, do estado conversacional ou do Gemini. A documentação detalhada Front–Back permanece para a próxima fase; nenhum frontend/fetch foi criado.

## Inventário de arquivos

Criadas em `src/main/java/br/fiap/daiji`:

- `assistant/BeneficiaryJourney`, `CheckinConversations`, `JourneyDecision`, `JourneyIntent`, `JourneyNaturalLanguage`, `MedicacoesIdsCommandHandler`.
- `controller/ConfirmacaoMedicacaoController`.
- `dao/ConfirmacaoMedicacaoDAO`.
- `dto/ConfirmacaoMedicacaoRequest`, `ConfirmacaoMedicacaoResponse`.
- `model/CanalCheckin`, `ConfirmacaoStatus`.
- `service/ConfirmacaoMedicacaoService`.

Classes alteradas:

- `CheckinService`: validação compartilhada e sobrecarga de canal; assinatura REST anterior preservada.
- `AssistantResponse`, `ResponseFormatter`: listagem adicional com IDs.
- `GeminiClient`, `HttpGeminiClient`, `GeminiProtocol`: contrato separado de interpretação das novas intenções.
- `TelegramConfiguration`, `TelegramUpdateReceiver`: ligação com a jornada, sem acoplamento ao polling.

Não há dependências ou variáveis de ambiente novas. `pom.xml`, propriedades, Services/DAOs de score, schema e scripts oficiais/legados permanecem intactos. O README recebe somente referência curta a este documento.

## Testes e execução

Novas suítes: `CheckinConversationsTest`, `BeneficiaryJourneyTest`, `ConfirmacaoMedicacaoDAOTest`, `JornadaApiTest`, `JourneyProtocolTest` e `JourneyReceiverTest`. Cobrem etapas, validações, cancelamento, expiração por Clock sem sleep longo, concorrência, confirmação única de check-in, ownership, transação, rollback, recursos JDBC, JSON/HTTP, intents inconsistentes, integração do receiver e preservação da adesão/score.

`JornadaApiTest` executa REST/Service/DAO reais contra H2 em memória usando a ConnectionFactory mockada. A fixture de testes CP09 complementa a CP06; `CURRENT_TIMESTAMP` em H2 emula o default de data Oracle. Ela não é migration, não altera scripts de produção e não é executada no startup. Há dados de dois beneficiários para testar isolamento real do SELECT. H2 não certifica o comportamento do driver Oracle real.

O cliente HTTP Gemini é mockado e usa credencial vazia. Telegram também é mockado. Nenhum teste acessa Oracle, Telegram, Gemini reais ou internet. Não houve validação real adicional do CP09 nesta execução.

Com Java 21 e dependências no cache:

```powershell
$env:JAVA_HOME = 'C:\Users\felip\.jdks\ms-21.0.10'
$env:TELEGRAM_BOT_ENABLED = 'false'
$env:GEMINI_ENABLED = 'false'
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1\plugins\maven\lib\maven3\bin\mvn.cmd' -o verify
```

Log local: `target/cp09-verify.log`. Relatórios: `target/surefire-reports`. Nenhum segredo foi inserido em arquivos versionáveis. Nenhum commit ou push foi feito.

Resultado final em 17/09/2026 às 22:07:11 -03:00: **`mvn -o verify` — BUILD SUCCESS**, tempo de execução informado pelo Maven de **01:35 min**. **576 testes, 0 falhas, 0 erros, 0 ignorados**. Os **449 testes anteriores permanecem inalterados e passando**; **127 novos testes CP09**: 44 de jornada/intents, 34 de máquina de estados, 16 de DAO/transação, 31 de REST/JDBC/isolamento/score, 1 de contrato HTTP Gemini e 1 de receiver Telegram. JAR executável gerado. Após o sucesso, foi finalizada somente a documentação pendente, sem novas alterações de implementação.
