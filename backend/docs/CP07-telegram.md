# CP07 — Telegram Daiji

O bot é um canal de consulta do MVP acadêmico. Usa Java 21, Spring Boot e `java.net.http.HttpClient`, sem dependências novas. Não diagnostica, prescreve, confirma ingestão nem oferece emergência ou monitoramento contínuo.

## Arquitetura e deploy futuro

```text
HOJE: Telegram → TelegramUpdateSource.getUpdates → TelegramPollingService
                                                       ↓
                                             TelegramUpdateReceiver
                                                       ↓
                                                  MessageRouter
                                                       ↓
                                                CommandHandler
                                                       ↓
                                              Services Daiji → DAO → Oracle

SAÍDA: AssistantResponse → ResponseFormatter → TelegramClient.sendMessage → Telegram

FUTURO: Telegram → controller Webhook HTTPS → TelegramUpdateReceiver → MessageRouter
```

`MessageRouter.route(String)` não recebe offset, cliente HTTP ou objeto de polling. Os handlers também não conhecem Telegram. `AssistantResponse` contém resultados estruturados mínimos: nome/valor/classificação, medicações e resumos de check-in. `ResponseFormatter` monta texto plano. Os serviços Daiji permanecem independentes de ambos os pacotes `assistant` e `telegram`.

`TelegramClient` é somente a interface de saída. `TelegramUpdateSource` é a interface de entrada por polling; `HttpTelegramClient` implementa ambas usando duas operações HTTP distintas. `TelegramUpdateReceiver.receive(TelegramUpdate)` valida chat privado e adapta texto/resposta. Um futuro controller HTTPS pode chamar esse mesmo receiver, preservando comandos, formatação e acesso ao banco. A configuração futura deve substituir apenas a criação do polling pela entrada webhook; validação/autenticação de webhook pertence a esse próximo trabalho.

Webhook não foi implementado: a demonstração roda localmente, sem domínio, URL pública, HTTPS de entrada ou ngrok. A máquina precisa de acesso de saída HTTPS ao Telegram. Não há Gemini, SDK, chave, prompts ou envio ao Google. No CP08, o caminho `naturalText` do router poderá encaminhar texto para um serviço de intenções, reutilizando os handlers e seus resultados estruturados.

## Configuração e execução

| Variável | Default | Finalidade |
| --- | --- | --- |
| `TELEGRAM_BOT_ENABLED` | `false` | Habilita os beans da integração |
| `TELEGRAM_BOT_TOKEN` | vazio | Credencial fornecida exclusivamente pela configuração externa, nunca versionada |
| `TELEGRAM_BOT_POLL_TIMEOUT_SECONDS` | `25` | Timeout do getUpdates, limitado a 1–50 segundos |

`application.properties` contém somente placeholders, sem credencial. Mantenha o token no ambiente do processo iniciado pelo terminal/IDE. Arquivos `.env` não são carregados automaticamente. Configure também `ORACLE_URL`, `ORACLE_USER` e `ORACLE_PASSWORD` conforme o README; os requisitos Oracle anteriores permanecem.

Com JDK 21 e Maven configurados, após definir o token no ambiente de forma privada:

```powershell
$env:TELEGRAM_BOT_ENABLED = 'true'
mvn spring-boot:run
```

Não imprima o ambiente nem habilite logs HTTP de baixo nível que incluam URI: a Bot API exige a credencial no caminho. O código não registra URLs, corpos, exceções de transporte, textos recebidos, IDs de chat ou dados consultados.

Quando disabled, não existem beans de cliente, receiver ou polling e nenhuma chamada Telegram é iniciada. Os serviços e endpoints existentes continuam disponíveis. Quando enabled sem token ou com formato inválido, Spring inicia normalmente e o polling permanece parado, com warning fixo. A validação local verifica apenas formato, não autenticidade; respostas 401/404 da API desativam o worker com warning seguro. Corrija a variável e reinicie o backend. Nenhuma mensagem de erro contém o valor configurado.

## Comandos e respostas exatas

Aceita espaços extras, caixa dos comandos e sufixo `@NomeDoBot`. IDs são inteiros positivos até `Integer.MAX_VALUE`, conforme os serviços de consulta existentes; argumentos adicionais são inválidos. Não há criação, recálculo ou atualização por comando.

`/start`:

```text
Olá! Eu sou o assistente Daiji.

Posso consultar informações do acompanhamento do MVP.

Comandos disponíveis:
/score <id>
/medicacoes <id>
/checkins <id>
/ajuda

Exemplo:
/score 1
```

`/ajuda` e `/help`:

```text
/score <id>
Consulta o Score Daiji mais recente.

/medicacoes <id>
Lista medicações cadastradas.

/checkins <id>
Mostra os 3 check-ins mais recentes.

/ajuda (ou /help)
Mostra os comandos.

O Score Daiji é demonstrativo e não substitui avaliação profissional.
```

`/score 1`, para o registro ilustrativo abaixo:

```text
🩺 Score Daiji

Rafael Almeida
Score atual: 97/100
Classificação: BAIXO

O Score Daiji é demonstrativo e não substitui avaliação profissional.
```

O handler usa `BeneficiarioService.buscarPorId` para nome/existência e `ScoreService.atual` para o score persistido. Não chama `recalcular`, POST, fórmula ou INSERT. A classificação persistida é exibida sem reinterpretá-la. Ausência do score: `Ainda não há score calculado para este beneficiário.` Ausência do beneficiário: `Beneficiário não encontrado.`

`/medicacoes 1`, para registros ilustrativos:

```text
💊 Medicações cadastradas

• Losartana — 50 mg — 08:00
• Medicamento X — 10 mg — 20:00
```

Usa `MedicacaoService.listar`, o mesmo método do GET do CP04, com validação do beneficiário e ordenação existente. Campos opcionais ausentes aparecem como `Não informado`. Lista vazia: `Nenhuma medicação cadastrada.` Não confirma ingestão e não recomenda alterações de tratamento.

`/checkins 1`, para um registro ilustrativo:

```text
📋 Check-ins recentes

16/09/2026 10:30
Estresse: 3/5
Sono: BOA
Alimentação: BOA
Humor: CALMO
```

Usa o novo método `CheckinService.recentes` → `CheckinDAO.recentes`, lendo os dados existentes de CHECKIN. Consulta por ID parametrizado, `ORDER BY data_checkin DESC, id_checkin DESC FETCH FIRST 3 ROWS ONLY`. Não altera a criação do CP03 nem o cálculo do CP05. O DTO `CheckinResumo` omite `resposta_texto`, canal e IDs; o SELECT nem lê a resposta livre. Campos ausentes recebem `Não informado`. Lista vazia: `Nenhum check-in registrado.` As datas são locais, sem inventar fuso para o TIMESTAMP Oracle.

Schema conferido somente na fonte oficial completa: anexo `25391e3f-f264-4922-9e0f-cc157273095d/pasted-text.txt`, identificado no CP04. O script legado não foi usado. Nenhum schema, script, migration ou tabela de vínculo foi alterado/criado.

`/score abc` ou `/score`:

```text
Formato inválido.
Use: /score <idBeneficiario>
Exemplo: /score 1
```

Para medicações/check-ins, o texto usa o comando correspondente. Texto natural ou comando desconhecido:

```text
Não entendi essa mensagem ainda.

Use /ajuda para ver os comandos disponíveis.

Em breve o assistente Daiji também poderá responder perguntas em linguagem natural.
```

Falha de consulta no Service/Oracle:

```text
Não foi possível consultar essa informação agora. Tente novamente em instantes.
```

## Polling, offset e erros

Um único executor inicia pelo `SmartLifecycle`, sem bloquear a thread principal. `getUpdates` usa long polling de 25 s por default, timeout HTTP de 35 s e conexão de 10 s; envio usa timeout de 15 s. `allowed_updates=["message"]`. Updates sem message, text ou chat ID e chats não privados são ignorados. Outros tipos não geram resposta.

Offset começa em zero, ordena o lote por `update_id` e avança para `update_id + 1` após cada tentativa, inclusive updates ignorados/malformados com ID válido. IDs inferiores ao offset não são processados novamente. A próxima chamada confirma os anteriores ao Telegram. O offset é somente em memória: reinícios podem reapresentar updates ainda não confirmados; não há promessa de exatamente uma vez entre processos. Execute somente uma instância de polling por bot.

Decisão de entrega do MVP: falha de envio não reprocessa o update, pois o Telegram pode já ter aceitado a mensagem antes de um timeout. Isso evita respostas repetidas na mesma execução, mas pode perder uma resposta; o usuário pode repetir o comando. Uma mensagem problemática não impede as seguintes. Não há fila persistente de reenvio.

Falhas de getUpdates aplicam backoff de 1, 2, 4, 8, 16 e no máximo 30 s, reiniciado após uma consulta bem-sucedida. Rate limit usa `retry_after` quando maior, limitado defensivamente a 300 s. Falha isolada de envio espera 1 s ou o `retry_after`. Há pausa mínima de 250 ms após cada lote para evitar loop agressivo caso o servidor devolva imediatamente. Os testes substituem a espera; não dependem de sleeps longos.

Shutdown interrompe HTTP/espera e aguarda o worker por até 5 s. Uma operação JDBC já em andamento depende do timeout/interrupção do driver; se ultrapassar o prazo, há warning seguro. O executor é daemon e não impede o encerramento da JVM.

HTTP não segue redirects. Respostas de erro e causas HTTP/JSON são descartadas na fronteira do cliente, mantendo somente categoria segura e retry-after. JSON malformado aplica backoff. Mensagens são JSON UTF-8 sem `parse_mode`; textos acima de 4000 unidades UTF-16 são divididos sem cortar emojis, respeitando o limite da Bot API.

Se já houver webhook configurado para esse bot, getUpdates pode falhar; o CP07 não remove nem configura webhook automaticamente. Conflito entre instâncias também causa falha/retry. Use um bot sem webhook ativo e uma única instância para a demonstração. Referência de contrato: [Telegram Bot API — getUpdates](https://core.telegram.org/bots/api#getupdates) e [sendMessage](https://core.telegram.org/bots/api#sendmessage).

## Privacidade e limites

**Não há autenticação/autorização Telegram nem vínculo permanente entre chat e beneficiário. Qualquer usuário do bot que conheça um ID pode consultar os dados daquele beneficiário.** Usar somente dados demonstrativos e público controlado. Chat privado é filtro de tipo de conversa, não comprovação de identidade. O login do CP02 não autentica o Telegram.

Não são retornados CPF, email, telefone, empresa, autenticação, fatores internos do score ou resposta livre do check-in. O nome aparece somente no score. Dados selecionados são enviados ao Telegram para responder ao usuário, sem persistir chat ID ou token no Oracle. Esta limitação precisa ser resolvida antes de uso com dados pessoais reais.

## Inventário

Criadas em `src/main/java/br/fiap/daiji`:

- `assistant/AssistantResponse`, `CommandHandler`, `ScoreCommandHandler`, `MedicacoesCommandHandler`, `CheckinsCommandHandler`, `MessageRouter`, `ResponseFormatter`.
- `telegram/TelegramClient`, `TelegramUpdateSource`, `HttpTelegramClient`, `TelegramApiException`, `TelegramUpdate`, `TelegramUpdateReceiver`, `TelegramPollingService`, `TelegramConfiguration`.
- `dto/CheckinResumo`.

Classes existentes alteradas: `dao/CheckinDAO` e `service/CheckinService`, apenas com a consulta recente adicional. Também alterados `application.properties` e a referência curta no README. `pom.xml`, outros Services/DAOs, controllers, testes anteriores e scripts SQL preservados. Nenhuma dependência adicionada; H2 já existente é usado exclusivamente nos testes.

## Testes e validação manual

Novas suítes: `MessageRouterTest`, `HttpTelegramClientTest`, `TelegramPollingTest`, `TelegramConfigurationTest`, `CheckinRecentesTest` e `TelegramDisabledApplicationTest`. Cobrem comandos e respostas exatas, ausência/erro, BAIXO/MEDIO/ALTO persistidos, ausência de recálculo, validação de IDs, nulls, Unicode, divisão de mensagens longas, contrato JSON, erros sanitizados, ciclo de vida e deduplicação. A consulta de check-ins é executada em H2 Oracle mode para verificar filtro, desempate e limite; mocks JDBC verificam fechamento de recursos. H2 não certifica compatibilidade real do driver Oracle.

Os testes HTTP usam `HttpClient` mockado e credencial vazia; não solicitam, inventam ou armazenam token. Os testes de configuração usam somente valores malformados para verificar rejeição segura. Testes anteriores mantêm JDBC simulado ou H2. Não há acesso a Telegram real, Oracle real ou Gemini nos testes.

Para reproduzir, com Java 21 e dependências no cache:

```powershell
$env:TELEGRAM_BOT_ENABLED = 'false'
mvn -o verify
```

Validação real depende do ambiente do responsável: habilitar o bot com seu token privado, iniciar o backend com configuração Oracle, abrir o chat privado e enviar `/start`, `/help`, `/score <ID existente>`, `/medicacoes <ID existente>` e `/checkins <ID existente>`. Comparar score/medicações com os GETs existentes, conferir os check-ins e experimentar IDs inválidos. Parar o Spring e confirmar que o worker encerrou. Nenhuma validação real do bot ou Oracle foi alegada nesta implementação: o token não foi fornecido e não é necessário para os testes.

Nenhum commit ou push foi feito.

Resultado em 17/09/2026: `mvn -o verify`, Java 21.0.10, **BUILD SUCCESS — 330 testes, zero falhas, erros ou ignorados**. São 238 testes anteriores preservados e 92 novos (45 router/handlers/formatter, 19 HTTP, 13 polling/receiver, 6 configuração, 8 consulta de check-ins e 1 contexto completo disabled). JAR executável gerado. Log local: `target/cp07-verify.log`; relatórios: `target/surefire-reports`.
