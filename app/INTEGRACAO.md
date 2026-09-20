# Integração do frontend oficial DAIJI

Base auditada: `a11361b` — Atualizacao do site, app e bot. Branch mantida: `integracao-final`. Contrato usado: o fornecido para o backend oficial `400f9eb`, de outro repositório. O backend deste checkout não foi usado como contrato nem alterado.

## Configurar desenvolvimento e produção

`BACKEND_URL` em **`app/js/api-config.js`** está configurado com o backend público oficial `https://daiji-deploy.onrender.com`, sem `/api` no final. Para desenvolvimento local, deixar esse valor vazio usa o fallback existente `http://localhost:8080`.

O frontend continua HTML/CSS/JavaScript estático, sem build, framework, pacote npm ou servidor Node necessário para usá-lo. Servir os arquivos por HTTP com o servidor estático de preferência. Ordem das dependências nas três páginas com REST: session.js → api-config.js → http.js → script da página. theme.js permanece na posição relativa anterior.

`DaijiHttp.request` recebe caminhos `/api/...`; ele monta a URL exclusivamente por `DaijiApiConfig.url`. O timeout por requisição é de 30 segundos (ajustado na auditoria de 20/09/2026) e não repete POST automaticamente. A configuração CORS usa `CORS_ALLOWED_ORIGINS`, com a origem efetiva do frontend.

## Auditoria inicial e mapa de páginas

| Página em app/ | Scripts próprios carregados | Situação |
| --- | --- | --- |
| index.html | theme.js | Entrada pública; sem REST |
| login.html | session.js, http.js, theme.js, login.js | Login aceitava qualquer email/senha; agora REST; adicionado api-config.js |
| cadastro.html | session.js, http.js, theme.js, cadastro.js | Simulava sucesso via daijiLogado; empresas ficavam desabilitadas; agora REST; adicionado api-config.js |
| score.html | session.js privado, http.js, theme.js, score.js | Check-in/score já usavam REST, mas com host fixo e ID em chaves soltas; adicionado api-config.js |
| personalizacao.html | session.js privado, theme.js, personalizacao.js | Validação de formulário e navegação existentes preservadas; botão de logout passa a funcionar |
| agendar.html | session.js privado, theme.js, agendar.js | Interações existentes, sem REST |
| comunidade.html | session.js privado, theme.js, comunidade.js | Interações existentes, sem REST |
| descobrir.html | session.js privado, theme.js, descobrir.js, Bootstrap externo | Interações existentes, sem REST |
| esqueceuSenha.html | theme.js e script inline | Valida formulário e volta ao login; não existe endpoint de recuperação no contrato fornecido; não integrado |

A busca por `localhost`, `127.0.0.1`, `/api/`, `fetch(` e `XMLHttpRequest` em JS/HTML identificou três URLs hardcoded em score.js e o único fetch em http.js. Não há XMLHttpRequest nem fluxo REST de medicações ou gestão no frontend atual. O campo de medicação na personalização e a pergunta do check-in não constituem um cadastro/confirmador REST de medicamentos. Não foram criadas telas ou endpoints para esses recursos.

Cinco scripts privados chamavam `DaijiSession.validar()`, método ausente. O session.js já protegia páginas com `data-private`, mas isso não resolvia o erro de método inexistente. O botão `data-logout` também não tinha handler. Os métodos existentes foram preservados.

Armazenamento encontrado: daijiSession temporária/persistente e daijiPerfil em session.js; daijiLogado no cadastro simulado; idBeneficiario avulso e cache visual daiji_checkin por beneficiário/data em score.js; preferências de tema em theme.js. O site institucional também possui tema, agendamentos locais e uma checagem legada de daijiLogado em assets/js/telemedicina.js; seus arquivos não foram modificados. Essa checagem legada usa apenas localStorage e não representa autenticação do backend nem reconhece sessões temporárias do app.

## Login, cadastro e sessão

- Login: `POST /api/auth/login`, somente `{email, senha}`. Email é aparado; senha é enviada sem trim. Somente HTTP 200 com LoginResponse válido cria sessão. 401, indisponibilidade, timeout, erro de rede, JSON inválido e falha de storage geram mensagens locais, sem stack trace. Envios simultâneos são bloqueados; mostrar/ocultar senha e lembrar permanecem.
- Cadastro: `POST /api/auth/cadastro`, somente `{idEmpresa, nome, cpf, email, dataNascimento, telefoneWhatsapp, senha}`. Empresa vem de `GET /api/empresas` e mantém o select existente; uma lista indisponível/inválida bloqueia envio e orienta recarregar. CPF/celular são enviados sem pontuação, data vem do input date como yyyy-MM-dd. Senha não é transformada nem persistida.
- **Não foi adicionado telefone:** o input `#celular` existente é mapeado para `telefoneWhatsapp`.
- Cadastro trata 400/409/415 e erros de serviço/rede. HTTP 201 usa o LoginResponse da própria resposta e segue para personalizacao.html. Se JSON/sessão falhar depois do 201, informa que o cadastro ocorreu e orienta login, mantendo o envio bloqueado; não faz outro cadastro nem login automático adicional.
- Sessão principal: `daijiSession.usuario`, contendo apenas idAutenticacao, idBeneficiario (inclusive null), nome, email e tipoUsuario, além dos metadados locais de sessão. Resposta incompleta/tipos inválidos não produzem sessão. Senha e campos extras não são copiados.
- `criarSessao(resposta, manterConectado)` usa sessionStorage por padrão ou localStorage com “Manter-me conectado”. Limpa chaves de sessão/ID/flag anteriores nos dois storages. A chave legada `idBeneficiario` é apenas um espelho no mesmo storage da sessão; para null ela fica ausente. O score consulta exclusivamente `obterIdBeneficiario()`, sem confiar em ID avulso. O getter prioriza a sessão da aba quando existe.
- `obterSessao`, `estaAutenticado`, `criarSessao`, `obterUsuario`, `logout` continuam exportados. Adicionados `validar` (booleano e redirecionamento para login) e `obterIdBeneficiario`. O botão existente `data-logout` é atendido por delegação de evento.
- Sessões antigas do protótipo são descartadas pelo formato versão 2: é necessário login real novamente. Isso é controle de navegação local, **não JWT, token ou autenticação/autorização persistente no servidor**.
- daijiPerfil continua recebendo nome/email para compatibilidade visual, sem misturar dados de outra conta. Cache de check-in e tema foram preservados.

## Check-in e score

- Check-in: `POST /api/beneficiarios/{idBeneficiario}/checkins`, campos nivelEstresse, qualidadeSono, qualidadeAlimentacao, humor e respostaTexto. O nível segue inteiro 1–5; humor permanece null no formulário existente. Não foram adicionados canal, IDs ao body ou outros campos.
- O formulário mantém suas perguntas obrigatórias; isso é uma escolha da interface existente e não muda o fato de o backend aceitar campos opcionais/JSON vazio. Na montagem do payload, todas as opções visuais de sono e alimentação são convertidas para BOM/BOA, REGULAR ou RUIM, reconhecidos pelo backend; os textos exibidos permanecem iguais. Humor continua null porque a tela não coleta essa informação.
- Leitura: `GET /api/beneficiarios/{idBeneficiario}/score`; 404 mantém “Score ainda não calculado”.
- Após check-in 201: `POST /api/beneficiarios/{idBeneficiario}/score/recalcular`, sem body; exige 201; depois GET do score atualizado. Falha do score não desfaz nem reenvia check-in confirmado.
- O frontend mantém a validação de idBeneficiario, valorScore numérico, classificação e data ISO. Não calcula score nem altera sua regra. Usuário com idBeneficiario null permanece com identificação necessária, sem inventar ID ou consultar endpoint com null.
- Removidas as três URLs completas de score.js. Somente api-config.js contém o host de desenvolvimento em código de produção.

## Arquivos modificados/criados e motivo

| Arquivo | Alteração |
| --- | --- |
| app/js/api-config.js (novo) | Ponto único de URL e composição dos caminhos REST |
| app/js/http.js | Fetch passa pela configuração central |
| app/js/session.js | Sessão de LoginResponse, validar, ID centralizado, limpeza e logout |
| app/js/login.js | Substitui login fake por POST real e tratamento de estados |
| app/js/cadastro.js | Lista empresas, envia cadastro exato, usa resposta como sessão e trata falhas |
| app/js/score.js | URL relativa, ID da sessão, evento de alteração de sessão e status 201 no recálculo |
| app/login.html | Uma tag script para api-config.js |
| app/cadastro.html | Uma tag script para api-config.js |
| app/score.html | Uma tag script para api-config.js |
| app/tests/integracao.cjs (novo) | Validação em navegador com REST inteiramente simulado |
| app/INTEGRACAO.md (novo) | Auditoria, configuração, testes e pendências |

**Visual:** nenhum CSS, imagem, cor, layout ou estrutura de formulário foi alterado. Nos HTMLs, somente as três inclusões de script acima. Mensagens de estado ocupam os elementos existentes. Nenhum arquivo do site institucional ou backend foi alterado.

## Validação executada em 20/09/2026

Análise estática: todos os JS de app/js compilados com `vm.Script`, revisão de dependências e usos de DaijiSession, payloads e endpoints, busca de URLs e armazenamento, revisão de diff e verificação de whitespace. Não há dependência de Node no app; o runtime Node/Playwright já disponível na máquina foi usado exclusivamente para testes.

Teste real de execução do frontend: **22 cenários passaram em Edge headless**, carregando os arquivos HTML/CSS/JS por servidor HTTP local. As chamadas REST foram interceptadas e respondidas por mocks; nenhum backend oficial/Oracle foi acessado. Fontes/CDNs externos foram bloqueados pelo harness, portanto isso não é uma auditoria visual pixel a pixel.

Cobertura: login 200/401/503, rede, JSON/resposta inválida, senha preservada no payload e ausente do storage, sessão temporária/persistente, null no ID e remoção de ID antigo, rejeição de sessão do protótipo, empresas/cadastro 201 e payload exato, cadastro 400/409/415/503, ausência de segundo cadastro após 201 inválido, empresas indisponíveis, storage bloqueado, envios simultâneos, funcionamento dos métodos nas quatro outras páginas privadas, logout e fluxo score 404 → check-in 201 → recálculo sem body → GET atualizado. Configuração testada com fallback e URL fictícia reservada apenas dentro do teste; não configura produção.

Para repetir em ambiente de teste com Node, Playwright e Edge disponíveis:

```text
node app/tests/integracao.cjs
```

Nesta máquina, foi usado Node 24.19.0 do runtime Codex e NODE_PATH apontando para seus módulos Playwright já instalados, sem instalar pacotes no repositório. O sandbox bloqueou a abertura do Edge; a execução ocorreu fora dele após aprovação.

## Pendências e limites

1. BACKEND_URL já aponta para o backend oficial no Render. Depois de obter a URL pública da Vercel, adicionar essa origem em `CORS_ALLOWED_ORIGINS` no Render. O CORS do backend não foi alterado nesta preparação.
2. Validar contra o backend oficial disponível, com conta autorizada, inclusive tempos de resposta e conexão real. Não foi iniciado o backend deste repositório nem criado registro no Oracle.
3. Timeout ajustado para 30 segundos por requisição. Testes com relógio virtual cobrem o limite, resposta aos 15 segundos, leitura do corpo e falhas nas três etapas do check-in, preservando a confirmação após HTTP 201. A latência real do backend publicado não foi medida nesta auditoria. Ver `AUDITORIA-TIMEOUT-SCORE.md`.
4. Não há tela de gestão ou medicação REST nem endpoint de recuperação de senha no contrato para integrar. Fluxos demonstrativos existentes foram preservados.
5. O backend não retorna token: os controles locais de sessão não substituem autorização de dados no servidor.

Sem git add, commit, push, merge, troca de branch ou deploy. Trabalho parado para revisão.
