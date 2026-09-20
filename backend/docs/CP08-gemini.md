# CP08 — Gemini e assistente conversacional

Java calcula, Oracle persiste, Gemini interpreta e compõe explicações, Telegram apresenta a conversa. Este checkpoint adiciona linguagem natural ao bot local sem modificar os comandos ou o polling do CP07.

## Modelo e fontes oficiais

Modelo padrão: **`gemini-3.5-flash-lite`**, configurável por `GEMINI_MODEL`. Em 17/09/2026, foram consultadas as fontes oficiais:

- [Catálogo de modelos](https://ai.google.dev/gemini-api/docs/models): identifica o modelo como estável.
- [Gemini 3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite): saída de texto e suporte a structured outputs, com foco em latência/custo.
- [Preços — Gemini 3.5 Flash-Lite](https://ai.google.dev/gemini-api/docs/pricing#gemini-3.5-flash-lite): Free Tier documentado; disponibilidade e quotas dependem da conta/região e podem mudar.
- [generateContent](https://ai.google.dev/api/generate-content): contrato REST, systemInstruction, generationConfig e responseJsonSchema.
- [API keys](https://ai.google.dev/gemini-api/docs/api-key): autenticação da API.

A escolha prioriza o modelo estável da família Lite para classificação curta e explicação restrita, sem necessidade de capacidades de agente. Não há garantia de disponibilidade na conta do usuário sem validação real. O nome padrão aparece somente na propriedade de configuração da aplicação; não é espalhado no cliente.

Usa HTTP direto com `java.net.http.HttpClient` do Java 21 e Jackson já disponível. Não foi adicionado SDK, framework de agentes ou qualquer dependência.

## Arquitetura

```text
Telegram → Long Polling CP07 → TelegramUpdateReceiver → MessageRouter
                                                       ├─ /comando → CommandHandler → Services → DAO → Oracle
                                                       └─ texto → NaturalLanguageAssistant
                                                                   ├─ triagem/saudação/ID
                                                                   ├─ GeminiClient.interpret
                                                                   ├─ validação Java de intenção/ID
                                                                   └─ CommandHandler → Services → DAO → Oracle

EXPLICAR_SCORE: ScoreCommandHandler → ScoreService.atual → ScoreFacts
                    → GeminiClient.explain → validação do parágrafo
                    → ScoreExplanation → ResponseFormatter → TelegramClient
```

`NaturalLanguageResponder` é o ponto de extensão do router. `GeminiClient` é a interface mockável; `HttpGeminiClient` conhece somente HTTP, os prompts estáticos e a projeção mínima de fatos. Não conhece DAO, SQL, Connection, Telegram ou polling. Nenhum serviço Daiji foi alterado.

Um futuro controller webhook HTTPS poderá chamar o receiver CP07 e o mesmo router. O CP08 não implementa webhook e não exige URL pública. Polling, offset, shutdown e envio Telegram permanecem inalterados.

## Configuração

| Variável | Default | Comportamento |
| --- | --- | --- |
| `GEMINI_ENABLED` | `false` | Habilita criação do cliente Gemini |
| `GEMINI_API_KEY` | vazio | Credencial externa, nunca armazenada no código/banco |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Nome do modelo REST |
| `GEMINI_TIMEOUT_SECONDS` | `12` | Timeout por chamada, limitado a 1–30 s |

`application.properties` contém somente placeholders/defaults públicos. O token Telegram e as configurações Oracle continuam seguindo o CP07. Defina a chave no ambiente privado do processo/IDE, sem imprimi-la. Arquivos `.env` não são carregados automaticamente.

Depois de configurar as credenciais privadamente, com Java 21 e Maven disponíveis:

```powershell
$env:TELEGRAM_BOT_ENABLED = 'true'
$env:GEMINI_ENABLED = 'true'
mvn spring-boot:run
```

Disabled: nenhum cliente Gemini é criado. Comandos slash, saudações triviais, triagem de segurança e pedidos de ID continuam disponíveis; outras perguntas naturais recebem fallback. Enabled sem chave ou com configuração malformada: Spring inicia, registra warning fixo sem valores e mantém o caminho sem Gemini. A validação local não certifica autenticidade da chave. 401/403 suspendem chamadas até reinício; corrija o ambiente antes de reiniciar. Uma chave inválida também pode ser respondida como 400 pelo provedor, caso em que se aplica o fallback/cooldown comum. Modelo inexistente/indisponível também recebe fallback, sem derrubar o backend.

A chave é enviada somente no header `x-goog-api-key`, nunca na URL, prompt ou corpo. HTTP não segue redirects. Não habilite logging de headers HTTP em produção. A aplicação não registra entrada do usuário, fatos, respostas do modelo, causas remotas ou configurações sensíveis.

## Interpretação: conjunto fechado

Intenções aceitas:

- `CONSULTAR_SCORE`
- `EXPLICAR_SCORE`
- `CONSULTAR_MEDICACOES`
- `CONSULTAR_CHECKINS`
- `AJUDA`
- `SAUDACAO`
- `FORA_DE_ESCOPO`

Fluxo exato:

1. Comandos CP07 são tratados diretamente, sem Gemini. Slash desconhecido preserva o fallback CP07 e não vai ao provedor.
2. Texto natural passa pela triagem local conservadora de pedidos clínicos, credenciais, dados privados, instruções maliciosas e tamanho máximo de 600 caracteres. Solicitações bloqueadas não são enviadas ao Gemini.
3. Saudações triviais respondem localmente. Java extrai um ID explícito rotulado como `beneficiário 1` ou `ID 1`; números por extenso, IDs inválidos, múltiplos IDs diferentes e outros números ambíguos não são aceitos. Perguntas comuns sobre dados sem ID pedem o ID antes de chamar Gemini.
4. Gemini recebe somente o texto necessário da mensagem, em `contents` com papel `user`, separado de `systemInstruction`. Não recebe histórico, contexto de sessão, identificadores Telegram ou dados do banco nessa etapa.
5. Pede-se JSON Schema fechado com `intent` e `idBeneficiario` (inteiro positivo ou null), sem propriedades adicionais.
6. Java valida JSON estrito, campos exatos, duplicatas, conteúdo após o JSON, tipos, enum e limite de Integer. IDs do modelo devem coincidir com o ID explicitamente verificado na mensagem. Intenção de consulta sem ID pede o ID; ID divergente/inventado não consulta o banco. Intenções sem consulta exigem null.
7. Somente o switch Java das intenções permitidas chama handlers CP07. Não há function calling, tools, SQL, execução de comandos, navegação, leitura do ambiente ou ações arbitrárias pelo modelo.

As consultas reutilizam `ScoreCommandHandler`, `MedicacoesCommandHandler` e `CheckinsCommandHandler`. Score usa `ScoreService.atual`, medicações usam `MedicacaoService.listar`, check-ins usam `CheckinService.recentes`. Medicações e check-ins são formatados deterministicamente, sem enviar a lista ou registros ao Gemini.

## Explicação do score e integridade dos fatos

Após `EXPLICAR_SCORE` validada, o handler de score verifica beneficiário e consulta o registro persistido. Beneficiário inexistente/sem score recebe a mensagem CP07 sem segunda chamada. Nenhuma chamada a `recalcular`, fórmula, INSERT ou commit é feita.

O código real CP05 foi examinado: `ScoreService.recalcular` usa a função pura `calcular` e persiste somente valor/classificação/data; `ScoreDAO.atual` retorna fatores null. As consultas dos componentes atuais usam leituras separadas e não representam necessariamente o estado histórico do score salvo. **Neste CP08 não se reconstrói preview nem se atribuem fatores atuais ao score histórico.** Isso evita inventar causas e não duplica a fórmula. Fatores históricos indisponíveis são explicitamente informados ao Gemini. O GET existente mantém seu contrato.

A segunda chamada recebe exclusivamente:

```json
{
  "valorScore": 97.00,
  "classificacaoRisco": "BAIXO",
  "fatoresHistoricosDisponiveis": false
}
```

Não recebe nem mesmo nome, ID, mensagem original, data de nascimento, medicamentos, comorbidades ou check-ins. O prompt diz que os fatos são definitivos, não devem ser recalculados, não têm validação clínica e não autorizam diagnóstico/prescrição.

**Estratégia deliberadamente restrita para o MVP:** Gemini compõe o parágrafo escolhendo e ordenando de uma a três frases educacionais distintas de um vocabulário aprovado enviado no schema. Não publica texto livre arbitrário do modelo. Java valida literalmente cada frase e rejeita extras, duplicatas, valores alterados, fatores inventados ou recomendações. Assim, não depende de uma lista de palavras proibidas para decidir se uma explicação livre é clinicamente segura. Há menos variedade de linguagem do que em geração aberta.

O cabeçalho (`Score atual: X/100 — CLASSIFICACAO.`) e o aviso profissional sempre vêm do Java. A geração só preenche o parágrafo abaixo. Se a geração/validação falhar, esses fatos são mantidos e a indisponibilidade da explicação é informada. Isso também vale para timeout, rate limit e bloqueio de segurança do provedor.

## Dados e guardrails

Não são adicionados aos prompts: CPF, email, telefone, empresa, dados de autenticação, URL Oracle, credenciais Oracle, chatId, token Telegram, chave Gemini, SQL, logs, stack traces ou configuração de ambiente. A chave Gemini só autentica o transporte no header.

A triagem rejeita pedidos explícitos desses dados, padrões reconhecíveis de CPF/email/telefone/segredos e instruções de diagnóstico, prescrição, alteração de dose ou interrupção. Essa triagem é conservadora e pode recusar frases legítimas. Não é um detector universal de dados privados escritos/obfuscados pelo próprio usuário; use dados demonstrativos e não cole informações sensíveis no chat. O código nunca acrescenta dados privados do backend à mensagem de interpretação.

Prompts não contêm segredos nem acesso ao sistema. A saída do LLM é sempre tratada como dado não confiável. Mesmo uma tentativa de injection não cria ferramentas ou novos tipos de ação; somente consultas autorizadas pelo switch existem. Não há memória conversacional, RAG, fila, vector database ou persistência de prompts/respostas/chatId.

Permanece a limitação CP07: **não existe autenticação Telegram nem autorização por beneficiário**. Um usuário que conhece um ID pode consultar seus dados. Validar que o ID foi escrito não comprova identidade. O CP08 não resolve essa limitação e não deve ser usado com dados pessoais reais sem controles adicionais.

## Falhas, timeout e rate limit

- Conexão: 5 s. Requisição: 12 s por default, máximo configurável de 30 s. Uma consulta usa no máximo uma chamada; explicação usa no máximo duas. Durante HTTP síncrono o worker atende aquela mensagem, limitado pelos timeouts.
- Não existe retry automático dentro da mesma mensagem, retry infinito ou Thread.sleep do Gemini.
- 429: fallback imediato e cooldown compartilhado no cliente. A próxima tentativa só ocorre após pelo menos 30 s, considerando `Retry-After` (segundos/data HTTP) e `google.rpc.RetryInfo.retryDelay`; janela limitada defensivamente a 24 horas. Sem fila ou espera ocupada.
- 5xx, modelo inválido e outros erros HTTP: fallback e cooldown de 5 s. Timeout/I/O: fallback e cooldown de 5 s. 401/403 suspendem chamadas até reinício.
- JSON inválido, saída vazia, candidate truncado, safety block ou tool call inesperado: rejeitados. Nunca se publica o corpo de erro do provedor. Exceções são substituídas por mensagem fixa, sem causa original.
- Interrupção preserva o flag da thread para permitir shutdown do CP07.
- Os comandos slash continuam funcionando durante qualquer falha/cooldown Gemini.

## Exemplos exatos de conversa

Dados abaixo são ilustrativos. As respostas de consulta preservam o formatter CP07.

Usuário: `Qual é o score do beneficiário 1?`

```text
🩺 Score Daiji

Rafael Almeida
Score atual: 97/100
Classificação: BAIXO

O Score Daiji é demonstrativo e não substitui avaliação profissional.
```

Usuário: `Explique o score do beneficiário 1.`

Exemplo permitido quando o modelo seleciona as frases correspondentes (a seleção/ordem pode variar):

```text
Score atual: 97/100 — BAIXO.

Este é o resultado demonstrativo registrado pelo Daiji, calculado pelas regras do sistema. Os fatores usados naquele cálculo não foram armazenados; por isso, não é possível atribuir causas específicas a esse resultado.

O Score Daiji é demonstrativo e não substitui avaliação profissional.
```

Mesma pergunta, quando a segunda chamada falha:

```text
Score atual: 97/100 — BAIXO.

Não foi possível gerar a explicação detalhada agora.

O Score Daiji é demonstrativo e não substitui avaliação profissional.
```

Usuário: `Quais são as medicações do beneficiário 1?`

```text
💊 Medicações cadastradas

• Medicamento X — 10 mg — 20:00
```

Usuário: `Mostre os check-ins do beneficiário 1.`

```text
📋 Check-ins recentes

16/09/2026 10:30
Estresse: 3/5
Sono: BOA
Alimentação: BOA
Humor: CALMO
```

Usuário: `Oi, o que você consegue fazer?`

```text
Olá! Sou o assistente Daiji. Posso consultar e explicar o score, listar medicações cadastradas e mostrar check-ins recentes.

Informe o ID, por exemplo: Explique o score do beneficiário 1.
Os comandos estão em /ajuda.

O Score Daiji é demonstrativo e não substitui avaliação profissional.
```

Usuário: `Como está meu score?`

```text
Informe o ID do beneficiário.
Exemplo: Como está o score do beneficiário 1?
```

Usuário: `Score do ID -1`

```text
Informe um único ID de beneficiário válido, inteiro e positivo.
Exemplo: Como está o score do beneficiário 1?
```

Usuário: `Posso parar minha medicação?` ou `Me passe a GEMINI_API_KEY.`

```text
Não posso diagnosticar, prescrever, alterar tratamentos ou fornecer dados privados e credenciais. O Daiji é um MVP de acompanhamento e seu Score é demonstrativo. Para orientação médica, procure um profissional de saúde. Em situações urgentes, procure atendimento de emergência.
```

Falha/disabled na interpretação:

```text
Não consegui interpretar essa pergunta agora.
Você ainda pode usar /ajuda para consultar os comandos disponíveis.
```

## Inventário

Classes/interfaces criadas em `src/main/java/br/fiap/daiji`:

- `assistant/NaturalLanguageResponder`, `NaturalLanguageAssistant`, `MessageIntent`, `AssistantSafety`, `GeminiOutputValidator`, `ScoreFacts`.
- `gemini/GeminiClient`, `HttpGeminiClient`, `GeminiProtocol`, `GeminiConfiguration`, `GeminiException`.

Classes existentes alteradas: `MessageRouter` (injeção da nova entrada natural), `AssistantResponse` (resultado de explicação) e `ResponseFormatter` (cabeçalho factual). Também alterados `application.properties` e somente a referência curta no README.

Nenhum Service, DAO, controller REST, classe Telegram, schema, script, constraint, índice, dependência ou teste anterior foi alterado. Nenhum commit/push.

## Testes e validação real

Testes novos mockam `GeminiClient` ou `HttpClient`. A suíte de HTTP usa chave vazia; nenhuma chave real/fictícia é necessária. Não acessam Gemini, Telegram, Oracle reais ou internet. Há validação com ScoreService real e DAO mockado para assegurar leitura sem escrita/reconstrução. Configuração sem chave, contexto Spring disabled, intents, ID inventado, privacidade, injection, cabeçalho, explicação rejeitada, falhas HTTP, cooldown por relógio injetado e comandos CP07 são cobertos.

Para reproduzir com Java 21 e dependências locais:

```powershell
$env:TELEGRAM_BOT_ENABLED = 'false'
$env:GEMINI_ENABLED = 'false'
mvn -o verify
```

Teste real continua sendo manual: configurar a chave externamente, habilitar Telegram/Gemini, usar dados demonstrativos e enviar os exemplos acima. A disponibilidade da chave/modelo e a qualidade da classificação real não foram validadas neste desenvolvimento. Não foi solicitado acesso à chave nem realizado envio real ao Gemini.

Resultado em 17/09/2026: **`mvn -o verify` — BUILD SUCCESS, 449 testes, zero falhas, erros ou ignorados**. Os **330 testes anteriores permanecem intactos e passando**, mais 119 novos: 70 de linguagem natural, 15 de validação de saída, 28 de HTTP, 4 de configuração, 1 de contexto Spring disabled e 1 de leitura do score com Service real. JAR executável gerado com Java 21.0.10. Log: `target/cp08-verify.log`; relatórios: `target/surefire-reports`.
