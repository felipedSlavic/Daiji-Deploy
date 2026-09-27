# Integração Daiji — correção da regressão em 27/09/2026

## Causa raiz e histórico

O commit `16978611a6acdcdb2acadabff9583f3aa9f49283` (26/09/2026, 22:46, “Atualiza frontend com altreraçãoes da bracnh fonrt”) substituiu os scripts integrados por versões de protótipo. A comparação com `cd97e09` mostrou a remoção das chamadas HTTP em cadastro/login/score, das inclusões de `session.js`, `api-config.js` e `http.js`, do seletor de empresa e da mensagem de cadastro. Os utilitários de integração e os endpoints continuavam no repositório.

A interrupção acontecia **antes do Controller**: o formulário não enviava request. Cadastro e login gravavam `daijiLogado`, `daiji_contas` e `daiji_usuario`, declaravam sucesso e redirecionavam. O score começava em 74, era calculado pelo navegador e o check-in só gravava `daiji_checkin`. Seu `done` não tinha usuário/data: uma conclusão antiga podia bloquear outras contas e outros dias.

A correção reaproveita os blocos de integração de `cd97e09`, adaptados às perguntas e controles atuais. Não houve reversão integral de arquivos visuais, alteração de CSS, migração Oracle, novo endpoint, mudança de regra do score ou alteração do Telegram.

## O que já existia e o que foi conectado

| Recurso | Suporte real encontrado | Comportamento final |
| --- | --- | --- |
| Cadastro | CadastroController → CadastroService → CadastroDAO; transação em BENEFICIARIO e AUTENTICACAO, BCrypt, commit/rollback, conflitos 409 | Envia formulário; cria sessão e redireciona somente após HTTP 201 e resposta válida |
| Login | AuthController → AuthService → AutenticacaoDAO/BeneficiarioDAO; valida hash | Envia e-mail/senha; usa LoginResponse, sem senha no storage |
| Empresa | EmpresaController → EmpresaOpcaoDAO | Seletor restaurado, carregado do backend; não inventa ID de empresa |
| Beneficiário | Consulta por ID na API e por e-mail no Service/DAO para Telegram | ID recebido do backend permanece interno e vincula operações |
| Medicação | GET/POST existentes em MedicacaoController → Service → DAO | Campo da personalização usa esses endpoints, com consulta prévia para evitar repetir o mesmo nome |
| Personalização clínica | ScoreDAO lê COMORBIDADE/BENEFICIARIO_COMORBIDADE, mas não há endpoint para gravar os campos do formulário | Doenças, diabetes e atividade ficam locais, separados por beneficiário |
| Check-in | CheckinController → CheckinService → CheckinDAO, canal APP | POST real antes de marcar conclusão; falhas mantêm respostas e não atribuem sucesso |
| Score | ScoreController → ScoreService → ScoreDAO → SCORE_RISCO_RENAL | GET real; após check-in confirmado, POST de recálculo e novo GET |
| Telegram | Journey → BeneficiarioService → BeneficiarioDAO.buscarPorEmail | Preservado, inclusive normalização e uso interno do ID |
| Comunidade/descobrir/agendar | Interface demonstrativa; não há contrato equivalente para os círculos, catálogo e agendamento atuais | Sessão restaurada; comunidade usa o nome autenticado. Interações visuais permanecem |

## Contratos utilizados

URL central existente: `app/js/api-config.js`, `https://daiji-deploy.onrender.com`. Nenhum host foi espalhado pelos scripts.

| Método e caminho | Entrada | Sucesso / retorno | Tratamento relevante |
| --- | --- | --- | --- |
| GET `/api/empresas` | Sem corpo | 200, lista `{idEmpresa,nome}` | Lista vazia/inválida ou falha bloqueia cadastro e orienta recarregar |
| POST `/api/auth/cadastro` | `{idEmpresa,nome,cpf,email,dataNascimento,telefoneWhatsapp,senha}` | 201, LoginResponse | 400 dados/empresa inválidos; 409 CPF/e-mail duplicado; 415 formato; demais falhas sem redirecionamento |
| POST `/api/auth/login` | `{email,senha}` | 200, LoginResponse | 401 credenciais inválidas; erros de rede/serviço/resposta não criam sessão |
| GET `/api/beneficiarios/{id}` | ID interno | 200, BeneficiarioResponse; 404 ausente | Contrato preservado; não é necessário à criação da sessão |
| GET `/api/beneficiarios/{id}/medicacoes` | ID da sessão | 200, lista MedicacaoResponse | Confere beneficiário de cada item antes de considerar a lista |
| POST `/api/beneficiarios/{id}/medicacoes` | `{nomeMedicamento,dosagem:null,horarioPrevisto:null}` | 201 | Sem confirmação, permanece no formulário |
| POST `/api/beneficiarios/{id}/checkins` | `{nivelEstresse,qualidadeSono,qualidadeAlimentacao,humor,respostaTexto}` | 201, CheckinResponse | 400/404/5xx e timeout não simulam conclusão nem disparam recálculo |
| POST `/api/beneficiarios/{id}/score/recalcular` | Sem corpo | 201, ScoreResponse | Falha não desfaz nem repete o check-in confirmado |
| GET `/api/beneficiarios/{id}/score` | ID da sessão | 200, ScoreResponse | 404: ainda sem score; erros não são substituídos por um valor fictício |

LoginResponse: `{idAutenticacao,idBeneficiario,nome,email,tipoUsuario}`. A sessão existente só copia esses campos; mantém-se na aba ou no localStorage quando “Manter-me conectado” estiver marcado.

Cadastro envia CPF/telefone sem pontuação, data ISO, e-mail aparado/minúsculo e senha sem transformação. `confirmarSenha` nunca é enviado. As validações do backend permanecem: empresa existente, limites em bytes, CPF com 11 dígitos, telefone com 10–15 dígitos, data válida não futura, e-mail válido e senha dentro do limite BCrypt. Erros são apresentados por mensagens locais, sem SQL/stack trace.

## Fluxos finais

**Cadastro:** formulário válido → empresas reais → POST cadastro → Controller → Service → DAO → INSERT BENEFICIARIO + INSERT AUTENTICACAO na mesma transação → commit → HTTP 201 com IDs → DaijiSession → personalização. HTTP 201 com resposta/sessão inválida informa que o cadastro ocorreu e pede login, sem reenviar automaticamente.

**Login:** POST com credenciais → verificação BCrypt → LoginResponse → sessão local de navegação. O redirect anterior foi mantido, limitado à mesma origem. Contas que existiam apenas no protótipo precisam ser cadastradas de verdade; não há migração de senhas locais.

**Personalização:** usa ID da sessão. Quando há medicação, consulta as existentes e cadastra o texto se o mesmo nome ainda não existir. O campo é texto livre: não se inferem dose, horário ou vários medicamentos a partir de uma frase. O restante fica em preferências locais por pessoa. Finalizar novamente com o mesmo nome não o duplica em envios sequenciais.

**Check-in:** perguntas atuais preservadas → mapeamento de sono/alimentação para valores reconhecidos pelo backend → POST com ID da sessão → somente HTTP 201 marca conclusão e cache do dia. Duração do sono, passos, água, pressão, glicemia e resposta sobre remédios entram no campo já existente `respostaTexto`, respeitando seu limite de 500 bytes. Humor continua null porque o formulário não o coleta. A resposta genérica sobre remédios não cria confirmações por medicação: não há informação suficiente para identificar cada registro.

**Score:** deixa de usar `healthScore` local. Busca valor, classificação e data persistidos; depois de check-in confirmado solicita recálculo e consulta o resultado. O cálculo existente usa idade, comorbidades já persistidas, adesão de medicações e os sete check-ins mais recentes. As observações livres não alteram a fórmula. Falha no recálculo/GET mantém o check-in e informa a atualização pendente. O cálculo não é disparado só por abrir a página.

## Armazenamento local mantido

| Dados | Motivo |
| --- | --- |
| `daijiSession`, espelhos `idBeneficiario`/`daijiLogado`, `daijiPerfil` | Utilitário de sessão existente; navegação e identificação, sem senha; persistência opcional de login |
| `daiji_personalizacao:{id}` | Doenças, diabetes, atividade e rascunho do campo medicação; faltam endpoints para parte dos dados. A lista oficial de medicações é consultada no backend |
| `daiji_checkin:{id}` com data | Cache visual de POST confirmado; impede bloqueio por outra pessoa/dia. Não é histórico oficial |
| Pontos do check-in | Gamificação demonstrativa; não são o score clínico/demonstrativo calculado pelo backend |
| Tema | Preferência de interface já existente |

O app deixa de criar/usar `daiji_contas` e `daiji_usuario` como identidade. Comunidade lê DaijiSession. O cache global antigo `daiji_checkin` e a flag global de diabetes não são usados para inferir identidade/conclusão/preferências de outra conta. Não houve limpeza indiscriminada do storage.

## URL publicada e CORS

Origem informada: `https://daiji-deploy.vercel.app`. Backend: `https://daiji-deploy.onrender.com`.

Verificação externa em 27/09/2026, aproximadamente 03:07 UTC: OPTIONS `/api/auth/cadastro` com Origin Vercel e método POST retornou **200**, `access-control-allow-origin: https://daiji-deploy.vercel.app`, `access-control-allow-headers: content-type` e métodos GET/POST/PUT/DELETE/PATCH/OPTIONS. HEAD no frontend retornou 200. A primeira tentativa expirou; a segunda confirmou a configuração publicada.

Nenhuma alteração de CORS em produção foi necessária. O backend continua usando `CORS_ALLOWED_ORIGINS`; manter a origem acima nessa variável no Render, sem barra final. O teste de configuração agora usa a origem real. Nenhum deploy foi realizado.

## Arquivos alterados

- `app/cadastro.html`: dependências de API/sessão, seletor obrigatório de empresa e área de mensagem restaurados.
- `app/login.html`, `app/score.html`, `app/personalizacao.html`: dependências restauradas; estado real do score e mensagens/logout na personalização.
- `app/agendar.html`, `app/comunidade.html`, `app/descobrir.html`: restabelecem o carregamento da sessão privada.
- `app/js/cadastro.js`, `app/js/login.js`: HTTP real, estados de erro e sessão após sucesso.
- `app/js/personalizacao.js`: preferências por beneficiário e persistência da medicação existente no contrato.
- `app/js/score.js`: check-in/score via API, cache por usuário/dia, preservação das perguntas atuais.
- `app/js/comunidade.js`: nome obtido da sessão autenticada.
- `app/tests/integracao.cjs`: adapta os testes às perguntas atuais e cobre cache/personalização/falhas.
- `backend/src/test/java/br/fiap/daiji/CadastroApiTest.java`: cadastro/login/medicação/check-in/score/Telegram ligados ao mesmo ID, usando H2.
- `backend/src/test/java/br/fiap/daiji/ConfiguredCorsApiTest.java`: origem Vercel real via variável de configuração.
- `app/INTEGRACAO.md`: este relatório substitui a auditoria desatualizada.

Nenhum arquivo de produção Java, schema, polling, configuração Gemini ou implementação Telegram foi alterado.

## Testes

Antes das alterações: `mvn -B test`, Java 21.0.10, **743 testes passaram**. A suíte frontend existente falhou no primeiro cenário de login, pois `daijiSession` não era criada. Os testes de timeout/sintaxe passaram.

Após a correção: execução final registrada em `backend/target/integration-final.log` e `backend/target/frontend-integration.log`. O backend usa H2/mocks nas suítes; o navegador usa Edge headless com API interceptada. Não foram criados registros de teste no Oracle publicado.

Cobertura adicional: ID de um segundo beneficiário (evita sucesso acidental com ID 1), busca por e-mail sem distinguir caixa/espaços, duplicidade 409, persistência APP/TELEGRAM, recálculo/consulta 85 no teste de dados ruins, medicação associada ao ID correto; no navegador, erros 400/404/503, ausência de falso sucesso, cache legado/ontem/outra pessoa/hoje, zero passos/água, diabetes por pessoa, medicação sem repetição sequencial e falha mantendo formulário.

Comandos:

```text
# backend, JAVA_HOME apontando para Java 21
mvn -B test
# raiz, Node + Playwright + Edge no ambiente de testes
node app/tests/integracao.cjs
```

Nesta máquina Node/Playwright são fornecidos pelo runtime já instalado; não foi adicionado pacote/build ao app. O sandbox bloqueou a abertura do Edge, então os testes de navegador foram executados com escalonamento autorizado e API simulada.

## Limitações importantes para a apresentação

- A correção está no checkout, ainda não publicada. O frontend online continua com a versão anterior até o deploy.
- Login real valida senha, mas o backend existente não emite token/sessão de autorização. DaijiSession é controle de navegação; não foi criado um sistema novo de autenticação.
- Preferências clínicas locais não sincronizam entre navegadores nem atualizam COMORBIDADE. O score só considera comorbidades já existentes no Oracle.
- A personalização adiciona uma medicação pelo texto informado; não edita/exclui uma anterior quando esse texto muda ou é apagado. O backend não oferece esses endpoints. A consulta prévia evita repetição sequencial, mas não é idempotência transacional entre abas concorrentes.
- Pontos, ranking, gráficos/cartões demonstrativos, comunidade, catálogo/agendamento e recuperação de senha não viraram dados oficiais do Oracle.
- O cache diário do check-in é conveniência local, não restrição global entre dispositivos/Telegram. Timeout pode ocorrer depois de uma gravação; não há retry automático de POST.
- A checagem legada de login do site institucional/Dr.Online usa localStorage e pode pedir novo login quando a sessão do app é apenas temporária; não representa autorização backend.
- O teste Oracle real e envio ao bot precisam ser feitos no ambiente publicado/local integrado antes da apresentação. As verificações externas desta execução foram de disponibilidade/CORS, sem cadastro real.

## Roteiro manual antes do commit

1. Sirva o checkout por HTTP em uma origem permitida (por exemplo `http://localhost:5500`) e use `app/cadastro.html`. Para testar a Vercel, publique o frontend revisado primeiro. Não use file://.
2. Cadastre um e-mail/CPF novos e autorizados para a demonstração, escolhendo uma empresa da lista. No Network, confirme POST `/api/auth/cadastro` → 201. Confira no Oracle:

   ```sql
   SELECT id_beneficiario, nome, email, data_cadastro
   FROM BENEFICIARIO WHERE LOWER(email) = LOWER(:email);
   ```

   Guarde o ID apenas para conferência técnica. Repita o cadastro e confirme 409, sem redirecionamento nem registro duplicado.
3. Na personalização, use “Sair da conta”. Faça login com a senha correta: 200 e mesmo ID interno. Teste também senha errada: deve permanecer na tela, sem sessão nova.
4. Abra o Perfil, informe preferências e uma medicação de teste. Finalize: GET/POST medicações para o ID correto. Confira a tabela MEDICACAO e finalize novamente com o mesmo texto para confirmar que não houve repetição.
5. Abra o check-in, responda sono/estresse/alimentação/remédios e preencha passos/água (zero é aceito). Se marcou diabetes, preencha glicemia e a opção de medição. Confirme POST checkins → 201 e registro em CHECKIN com canal APP e o ID esperado.
6. Confira POST score/recalcular → 201 seguido de GET score → 200. Compare valor/classificação/data com SCORE_RISCO_RENAL. Recarregar não deve mostrar o antigo valor fixo 74.
7. No Telegram envie “Quero fazer meu check-in” e o mesmo e-mail. O bot deve localizar o cadastro sem pedir/exibir ID. Use `/score seu@email.com` e `/medicacoes_ids seu@email.com` para conferir os dados compartilhados.
8. No navegador, teste outra conta e um cache de dia anterior: não devem herdar o bloqueio/preferências do primeiro usuário. Simule falha de rede no cadastro/check-in e confirme que os campos são mantidos e nenhuma conclusão fictícia aparece.

Sem commit, push ou deploy nesta execução.