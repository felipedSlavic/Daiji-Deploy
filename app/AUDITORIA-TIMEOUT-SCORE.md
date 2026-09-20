# Auditoria de timeout e score — 20/09/2026

## Escopo e evidência

Inspeção do checkout Daiji-Deploy e testes locais sem Oracle. Estado inicial limpo.
O usuário informou estresse **1** no caso observado; não forneceu histórico, payload capturado,
idade, comorbidades ou resposta com fatores do recálculo daquela conta.
Portanto, reproduzimos o mecanismo que produz 100, mas não afirmamos ter consultado os registros reais.
Não foi possível comparar os JS publicados com o checkout: as tentativas de leitura pública falharam.
Nenhuma alteração em código de produção do backend, fórmula, pesos ou layout.
Os exemplos de diagnóstico abaixo descrevem o payload anterior à correção.
A conversão aprovada e implementada posteriormente está registrada ao final.

## Timeout

`app/js/http.js` usava um limite fixo de 10000 ms para cada `DaijiHttp.request`.
`Promise.race` rejeita com `TimeoutError` e chama `AbortController.abort()`.
O prazo inclui o fetch e a leitura do corpo nas chamadas que consomem JSON.
O ajuste de produção é exclusivamente **10000 → 30000 ms**.

Afeta as seis chamadas REST existentes:

| Método | Caminho | Corpo da resposta lido |
| --- | --- | --- |
| POST | /api/auth/login | Sim |
| GET | /api/empresas | Sim |
| POST | /api/auth/cadastro | Sim |
| GET | /api/beneficiarios/{id}/score | Sim, na consulta inicial e após recálculo |
| POST | /api/beneficiarios/{id}/checkins | Não; usa status |
| POST | /api/beneficiarios/{id}/score/recalcular | Não; usa status |

Cada etapa recebe seu próprio prazo; não há prazo único de 30s para o fluxo inteiro.
Não há retry automático. Abort no cliente não prova rollback no servidor.
Antes de receber 201, o aviso diz que o check-in pode ter sido registrado.
Depois de receber 201, a tela marca o check-in concluído e salva o cache visual.
Timeout no recálculo ou no GET seguinte mantém a confirmação e informa:
“Check-in registrado, mas não foi possível atualizar o score neste momento.”
O score anterior é preservado. Nenhuma mudança nesse tratamento foi necessária.

O limite de 10s explica a interrupção no cliente. A causa da latência Render/Oracle
não foi medida; não atribuímos o atraso a cold start ou banco sem evidência.
O fluxo abre conexões JDBC separadas para as consultas/escritas; não há pool nesta factory.

## Payload ruim reproduzido na tela

Selecionando Péssimo, estresse 1, Não me alimentei bem e Não tomei hoje,
mantendo o horário inicial, o navegador envia exatamente:

```json
{
  "nivelEstresse": 1,
  "qualidadeSono": "Péssimo",
  "qualidadeAlimentacao": "Não me alimentei bem",
  "humor": null,
  "respostaTexto": "Horário de dormir: 22:30 | Medicação: Não tomei hoje"
}
```

O frontend não transforma as opções em categorias do cálculo. Humor não é perguntado.
Estresse 1 significa menor penalidade na regra; 5 significa maior penalidade.

## Persistência e consultas

`CheckinController` recebe `CheckinRequest`; o DTO exige estresse inteiro.
`CheckinService` valida 1–5 quando presente e tamanho dos textos, sem validar vocabulário.
Mantém textos não vazios, converte vazio em null, define canal APP e usa o beneficiário da URL.
`CheckinDAO.criar` grava os cinco campos, beneficiário e canal; o banco define a data.
Obtém ID/data e efetua commit antes de retornar HTTP 201.

`ScoreService.recalcular` consulta nascimento, comorbidades, adesão e os últimos check-ins.
É `ScoreDAO.ultimosCheckins` que lê para o cálculo: mesmo beneficiário,
`ORDER BY data_checkin DESC, id_checkin DESC FETCH FIRST 7 ROWS ONLY`.
`CheckinDAO.recentes` lista três registros para outro fluxo; não é usado no recálculo.
O recém-criado participa se estiver entre os sete mais recentes pela data/ID.
Registros com datas futuras poderiam antecedê-lo; isso não foi observado em dados reais.
O teste verifica inclusão do novo, desempate por ID, exclusão do oitavo e isolamento por beneficiário.

O POST de recálculo persiste um novo score com commit. GET retorna o último score persistido,
ordenado por data de cálculo e ID; não recalcula e não retorna os fatores históricos.
O frontend aguarda o POST e a consulta inicial antes do GET final e exibe `valorScore` recebido.

## Regra existente e cálculo do 100

`score = clamp(100 - soma das penalidades, 0, 100)`.
Hipertensão: 10; diabetes: 10; outras comorbidades distintas: 5 cada, máximo 15.
Idade: abaixo de 40 = 0; 40–59 = 3; 60–69 = 5; 70+ = 10; nascimento null = 0.
Adesão usa CONFIRMADO/(CONFIRMADO+PERDIDO) de todas as confirmações de medicação:
90%+ = 0; 75%+ = 5; 50%+ = 10; abaixo de 50% = 15; sem dados = 0.
“Não tomei hoje” em `respostaTexto` não cria confirmação PERDIDO e não afeta adesão.

Nos sete check-ins, somam-se pontos e máximos dos componentes reconhecidos:

| Componente | Pontos | Máximo |
| --- | --- | --- |
| Estresse válido 1–5 | max(0, estresse − 2) | 3 |
| Sono/alimentação BOA ou BOM | 0 | 2 |
| Sono/alimentação REGULAR | 1 | 2 |
| Sono/alimentação RUIM | 2 | 2 |
| Humor CALMO/BEM/BOM/FELIZ/POSITIVO | 0 | 2 |
| Humor REGULAR/NEUTRO | 1 | 2 |
| Humor TRISTE/ANSIOSO/ESTRESSADO/IRRITADO/RUIM/NEGATIVO | 2 | 2 |
| Null ou texto desconhecido | Ignorado | Ignorado |

Há normalização de acentos, caixa e espaços, mas não de sinônimos.
Razão pontos/máximo: até 25% = 0; até 50% = 5; até 75% = 10; acima = 15.
Sem componentes reconhecidos = 0 e `dadosCheckinsSuficientes=false`.
A flag torna-se true com qualquer componente válido, mesmo se os outros forem ignorados.

Para o payload ruim acima, em uma conta jovem sem outras penalidades e sem histórico:

1. Estresse 1: max(0, 1−2) = 0 pontos, máximo 3.
2. Péssimo → PESSIMO: desconhecido; excluído.
3. Não me alimentei bem → NAO ME ALIMENTEI BEM: desconhecido; excluído.
4. Humor null: excluído. Texto livre não participa.
5. Razão = 0/3 = 0%; penalidade check-ins = 0.
6. Demais penalidades = 0; score = clamp(100−0, 0, 100) = **100**.

Isso identifica uma incompatibilidade semântica de integração: o frontend envia rótulos
que o cálculo não reconhece e o backend aceita/ignora silenciosamente.
Não se reproduziu perda na persistência nem seleção de beneficiário incorreto.
Histórico também pode manter 100 legitimamente pela agregação existente:
um novo check-in com estresse 5 e textos desconhecidos, mais seis bons canônicos,
gera 3/(3+6×7) = 6,67%, abaixo do limiar de 25%.

## Testes e proposta para revisão

`app/tests/http-timeout.cjs`: relógio virtual comprova ausência de abort aos 10s e 29999ms,
timeout aos 30s, sucesso aos 15s, prazo da leitura do corpo e ausência de retry.
É executado pela suíte existente `app/tests/integracao.cjs`, ampliada com timeout nas três
etapas e captura do payload ruim da tela (API simulada).

`ScorePublicadoAuditTest`: Controller/DTO/Service/DAO reais com H2 em memória.
Verifica conteúdo persistido por outra conexão após 201, leitura usada no cálculo e GET final:

| Cenário isolado, demais penalidades zero | Score esperado pela regra atual |
| --- | --- |
| Bom da tela: 1, Ótimo, Segui bem minha dieta, null | 100 (textos ignorados) |
| Ruim da tela: 1, Péssimo, Não me alimentei bem, null | 100 |
| Bom canônico: 1, BOM, BOA, null | 100 |
| Ruim canônico: 1, RUIM, RUIM, null | 90 (4/7; penalidade 10) |
| Ruim da tela com estresse 5 | 85 (3/3; penalidade 15) |
| Ruim canônico com estresse 5 | 85 (7/7; penalidade 15) |
| Sem check-ins ou com campos null | 100, dados insuficientes |
| Novo ruim estresse 5 com seis bons anteriores | 100, diluição na janela |

Correção mínima proposta na auditoria e **posteriormente aplicada após aprovação**: mapear opções existentes para o vocabulário
já reconhecido pelo backend antes do POST, sem calcular score no frontend:
Péssimo→RUIM, Regular→REGULAR, Bom/Ótimo→BOM;
Segui bem minha dieta→BOA, Alguns excessos→REGULAR, Não me alimentei bem→RUIM.
Correspondência aprovada pelo usuário. Estresse e humor não foram alterados.
Essa correção não normaliza check-ins antigos, não cria confirmações de medicação
e não muda a agregação de sete registros; portanto não garante queda após cada check-in.
Fórmula, pesos, defaults e política de histórico ficam intactos para revisão.

Resultados da auditoria inicial: 26 cenários de navegador aprovados, mais verificações unitárias
do timeout; sintaxe válida nos 13 scripts de app/js e app/tests.
Backend: 102 testes existentes aprovados (CheckinApiTest: 24, CheckinRecentesTest: 8,
ScoreApiTest: 22, ScoreServiceTest: 48) e oito novos testes H2 aprovados.
A primeira execução do teste novo revelou duas tabelas ausentes na fixture H2;
foram adicionadas exclusivamente à preparação desse teste e os oito casos passaram na reexecução.
`git diff --check` sem erros, apenas avisos de normalização LF/CRLF do Git.

Reprodução: `node app/tests/integracao.cjs` com Playwright/Edge disponíveis;
`mvn -o -f backend/pom.xml -Dtest=ScorePublicadoAuditTest,ScoreServiceTest,ScoreApiTest,CheckinApiTest,CheckinRecentesTest -Dtelegram.bot.enabled=false -Dgemini.enabled=false test`
com Java 21 e dependências Maven locais. Nesta máquina foram usados os runtimes
existentes por caminho absoluto; nenhum pacote instalado no repositório.
O Edge foi executado com autorização fora do sandbox; todas as chamadas REST foram interceptadas.
Logs Maven estão em backend/target (ignorado pelo Git).

## Correção mínima aprovada

Em `app/js/score.js`, a montagem do payload converte todas as opções reais da tela:

| Campo | Opção visual preservada | Valor na API |
| --- | --- | --- |
| qualidadeSono | Péssimo | RUIM |
| qualidadeSono | Regular | REGULAR |
| qualidadeSono | Bom | BOM |
| qualidadeSono | Ótimo | BOM |
| qualidadeAlimentacao | Segui bem minha dieta | BOA |
| qualidadeAlimentacao | Alguns excessos | REGULAR |
| qualidadeAlimentacao | Não me alimentei bem | RUIM |

Opções sem mapeamento são rejeitadas antes do POST, sem fallback silencioso para bom/null.
O valor numérico do estresse permanece inalterado. Horário e medicação continuam somente
em respostaTexto. Humor é utilizado pelo ScoreService, mas não há pergunta/opção visual de
humor nesta tela; manter null representa dado não coletado, não perda de uma resposta.
Não foi inventada resposta nem adicionada pergunta.

Os testes de navegador cobrem as 12 combinações de sono/alimentação e conferem a lista
completa de opções reais do HTML, payload, humor null, estresse 1, texto livre preservado e
um único POST de check-in. Os testes H2 cobrem as nove combinações canônicas distintas,
incluindo bom=100 e ruim=90 com estresse 1, sem outras penalidades/histórico.
Casos antigos com textos desconhecidos permanecem como documentação da causa e do histórico.
O timeout de 30000 ms e testes de ausência de retry continuam preservados.

Validação após a correção: **37 cenários de navegador aprovados**, além dos testes unitários
de timeout; **117 testes do backend aprovados**, incluindo 15 de ScorePublicadoAuditTest.
Sintaxe válida nos 13 scripts de app/js e app/tests; `git diff --check` sem erros.
Nenhuma alteração em HTML, CSS ou backend/src/main.

Nenhum commit, push, merge ou deploy. Nenhuma escrita no Oracle real.
