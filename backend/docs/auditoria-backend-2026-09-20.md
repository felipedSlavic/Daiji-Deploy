# Auditoria técnica do backend oficial — 20/09/2026

Escopo: somente este repositório Daiji-Sprint04. Nenhum frontend ou backend de outro repositório foi acessado/sincronizado. Não houve deploy, alteração de banco real, git add, commit, push, merge ou troca de branch.

## 1. Estado inicial e estrutura

Árvore Git limpa no início. Análise dos fontes realizada antes de editar arquivos. São 98 classes/arquivos Java de produção, distribuídos em `controller`, `service`, `dao`, `dto`, `model`, `config`, `factory`, `telegram`, `gemini` e `assistant`, além de `DaijiApplication`.

- Java 21; Spring Boot 3.5.16; Maven. Sem Maven Wrapper no repositório.
- Dependências: Spring Web/MVC, spring-security-crypto (BCrypt), Oracle JDBC ojdbc11 23.26.3.0.0; Spring Boot Test e H2 somente em testes. Nenhuma dependência alterada.
- JDBC puro: `ConnectionFactory` usa `DriverManager`, sem DataSource/pool, JPA ou Hibernate. `db.url`, `db.user`, `db.password` vêm de `ORACLE_URL`, `ORACLE_USER`, `ORACLE_PASSWORD`.
- 12 classes no pacote controller: 10 controllers REST e 2 handlers de exceções; 11 classes no pacote service; 13 no pacote dao; 19 DTOs; 8 models/enums/projeções.
- `assistant` roteia comandos, valida respostas da IA e controla o check-in conversacional; Telegram e Gemini são adaptadores opcionais.
- 29 arquivos Java de teste existentes: testes unitários, MockMvc com JDBC simulado e integração com H2 em memória no modo Oracle. Não havia teste de CORS.
- Não há Dockerfile, .dockerignore, pipeline de deploy, profiles específicos nem endpoint de health/readiness. O plugin Spring Boot já gera JAR executável.

Problemas comprovados:

1. `PATCH /api/gestao/encaminhamentos/{idEncaminhamento}` existe, mas seu preflight era rejeitado pelo CORS com 403.
2. `idProfissional` fracionário no JSON era truncado pelo Jackson: os testes com 1.5 e 2.9 criavam encaminhamento (201) para IDs inteiros diferentes do valor enviado.
3. Cadastro com `Content-Type: text/plain` era capturado pelo handler genérico e devolvia 500, quando deveria ser 415.
4. Não havia associação de `PORT` a `server.port`; CORS estava fixo nas quatro origens locais, exigindo recompilação para configurar o APP hospedado.

Limitação crítica preexistente: login verifica BCrypt e retorna identificação, mas não cria sessão/token nem autoriza chamadas subsequentes. APIs de beneficiário/gestão e comandos Telegram operam com IDs fornecidos pelo cliente. Isso impede considerar o backend pronto para exposição pública com dados reais. Uma nova arquitetura de autenticação não foi introduzida nesta auditoria.

## 2. Alterações e git diff

| Arquivo | Alteração e motivo |
| --- | --- |
| `src/main/java/br/fiap/daiji/config/CorsConfiguration.java` | Inclui PATCH; lê lista explícita de origens configurável; rejeita curinga, origem inválida, caminhos, query, fragmento e credenciais na URL. |
| `src/main/resources/application.properties` | `server.port=${PORT:8080}` e `app.cors.allowed-origins` via `CORS_ALLOWED_ORIGINS`, mantendo as quatro origens locais como padrão. |
| `src/main/java/br/fiap/daiji/dto/EncaminhamentoRequest.java` | Reutiliza o desserializador de inteiro estrito já existente, evitando truncar o ID do profissional. O campo exige número inteiro JSON. |
| `src/main/java/br/fiap/daiji/controller/CadastroExceptionHandler.java` | Retorna 415 com mensagem genérica para tipo de conteúdo não suportado. |
| `src/test/java/br/fiap/daiji/CadastroApiTest.java` | Regressão de content type incompatível, sem acessar persistência. |
| `src/test/java/br/fiap/daiji/GestaoApiTest.java` | Regressões para IDs fracionários, sem criar encaminhamentos. |
| `src/test/java/br/fiap/daiji/CorsApiTest.java` (novo) | Testa quatro origens locais, preflight JSON/PATCH, rejeições, escopo `/api/**` e CORS em resposta de erro. |
| `src/test/java/br/fiap/daiji/ConfiguredCorsApiTest.java` (novo) | Testa origem externa explícita, substituição das origens locais, rejeição de host semelhante e resolução de PORT. |
| `src/test/java/br/fiap/daiji/config/CorsConfigurationTest.java` (novo) | Rejeita configurações de origem inseguras ou inválidas. |
| `README.md` | Link para esta auditoria e orientações atuais de execução. |
| `docs/auditoria-backend-2026-09-20.md` (novo) | Relatório, contratos REST, evidências e pendências. |

Services, DAOs, models, ConnectionFactory, regras de score, integrações externas, dependências e rotas permanecem inalterados. Não houve remoção de funcionalidades. A validação do DTO e a resposta de erro de cadastro foram corrigidas, conforme evidências acima.

## 3. Build e 4. Testes

Ambiente: JDK `C:/Users/felip/.jdks/ms-21.0.10`; Maven disponível em `C:/Program Files/JetBrains/IntelliJ IDEA Community Edition 2025.1/plugins/maven/lib/maven3/bin/mvn.cmd`. Esses caminhos são somente da máquina de auditoria, não são dependências da aplicação.

Comandos Maven executados com `TELEGRAM_BOT_ENABLED=false` e `GEMINI_ENABLED=false` no processo, sem chamadas a serviços reais:

| Etapa | Comando | Resultado |
| --- | --- | --- |
| Inicial | `mvn -o -B clean verify` | BUILD SUCCESS; 674 testes, 0 falhas, 0 erros, 0 skipped. Inclui clean, compile, testes e package/repackage. |
| Reprodução antes das correções | `mvn -o -B "-Dtest=CorsApiTest,CadastroApiTest#tipoDeConteudoIncompativelRetorna415,GestaoApiTest#idProfissionalFracionarioNaoPodeCriarParaOutraPessoa" test` | 14 testes; 4 falhas esperadas: PATCH 403, content type 500 e dois IDs fracionários aceitos como 201. Defeitos do código, independentes de serviços externos. |
| Correções direcionadas | `mvn -o -B "-Dtest=CorsApiTest,ConfiguredCorsApiTest,CorsConfigurationTest,CadastroApiTest,GestaoApiTest" test` | BUILD SUCCESS; 177 testes, 0 falhas, 0 erros, 0 skipped. Compilação de produção/testes incluída. |
| Final | `mvn -o -B clean verify` | BUILD SUCCESS em 1 min 08 s; 706 executados/aprovados, 0 falhas, 0 erros, 0 skipped. JAR executável gerado. |

Nenhum teste foi desabilitado. Os avisos de autoanexação do Mockito/Byte Buddy no Java 21 e avisos de compilação de testes existentes não foram mascarados. Relatórios detalhados são gerados em `target/surefire-reports` (ignorado pelo Git).

Total final confirmado também somando os XMLs Surefire: **706 testes em 32 classes; 32 casos a mais que a suíte inicial**. Logs desta execução ficaram no diretório temporário da máquina (`daiji-audit-initial.log`, `daiji-audit-regression-before.log`, `daiji-audit-regression-after.log`, `daiji-audit-final.log`), fora do Git.

Teste adicional do artefato: iniciado com `java -jar`, `PORT=0` (porta atribuída pelo sistema), `CORS_ALLOWED_ORIGINS=https://app.example.test` e configuração Oracle fictícia apontando para loopback, sem chamada JDBC. Telegram/Gemini foram explicitamente habilitados com token/chave vazios: a aplicação iniciou e confirmou os dois fallbacks. Requisições HTTP reais de OPTIONS/PATCH retornaram 200 para a origem configurada e 403 para localhost e host não autorizado; POST de login vazio retornou 401 com CORS antes de qualquer consulta. Processo encerrado ao terminar. O JAR inclui driver Oracle e dependências Spring; não contém `.idea`, `.env` ou classes de teste.

## 5. API e contratos para o APP

Base local: `http://localhost:8080`. Todas as rotas abaixo foram mapeadas a partir dos controllers, DTOs e services; os fluxos principais têm cobertura automatizada. Essa validação não equivale a um teste ponta a ponta com o frontend, que não foi acessado.

`{id}`, `{idBeneficiario}`, `{idMedicacao}` e `idProfissional` usam Integer; `idEncaminhamento` e IDs de score/confirmação usam Long. Tipos incompatíveis/overflow retornam 400. Beneficiário ausente retorna 404 nos fluxos de consulta/escrita correspondentes. Falha SQL nos fluxos REST retorna 500 genérico, sem detalhes internos. GET não recalcula score.

| Método | Rota | Request esperado | Resposta principal |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | JSON `{email, senha}` | 200 LoginResponse; 401 credenciais inválidas |
| POST | `/api/auth/cadastro` | JSON com exatamente `idEmpresa, nome, cpf, email, dataNascimento, telefoneWhatsapp, senha` | 201 LoginResponse; 400 entrada/empresa inválida; 409 CPF/e-mail duplicado; 415 tipo de conteúdo incompatível |
| GET | `/api/empresas` | Sem body/parâmetros | 200 lista de `{idEmpresa, nome}` |
| GET | `/api/beneficiarios/{id}` | ID na URL | 200 BeneficiarioResponse; 404 ausente |
| POST | `/api/beneficiarios/{idBeneficiario}/checkins` | JSON `{nivelEstresse, qualidadeSono, qualidadeAlimentacao, humor, respostaTexto}` | 201 CheckinResponse, canal APP |
| GET | `/api/beneficiarios/{idBeneficiario}/medicacoes` | ID na URL | 200 lista de MedicacaoResponse (pode ser vazia) |
| POST | `/api/beneficiarios/{idBeneficiario}/medicacoes` | JSON `{nomeMedicamento, dosagem, horarioPrevisto}` | 201 MedicacaoResponse |
| POST | `/api/beneficiarios/{idBeneficiario}/medicacoes/{idMedicacao}/confirmacoes` | JSON somente `{statusConfirmacao}`: PENDENTE, CONFIRMADO ou PERDIDO | 201 ConfirmacaoMedicacaoResponse; 404 se medicação não pertence ao beneficiário informado |
| GET | `/api/beneficiarios/{idBeneficiario}/score` | Sem body | 200 ScoreResponse salvo; 404 se não há score |
| POST | `/api/beneficiarios/{idBeneficiario}/score/recalcular` | Sem body | 201 ScoreResponse com fatores; cria histórico |
| GET | `/api/gestao/dashboard` | Query opcional `idEmpresa` | 200 DashboardResponse |
| GET | `/api/gestao/beneficiarios` | Query opcional `idEmpresa` | 200 lista de GestaoBeneficiarioResponse |
| GET | `/api/gestao/casos-atencao` | Query opcional `idEmpresa` | 200 mesma projeção, somente ALTO no score atual |
| GET | `/api/gestao/profissionais` | Sem body/parâmetros | 200 lista de ProfissionalResponse |
| POST | `/api/gestao/beneficiarios/{idBeneficiario}/encaminhamentos` | JSON `{idProfissional, dataConsulta}`; data opcional | 201 EncaminhamentoResponse, status inicial PENDENTE |
| GET | `/api/gestao/encaminhamentos` | Queries opcionais `idEmpresa`, `status` | 200 lista de EncaminhamentoResponse |
| PATCH | `/api/gestao/encaminhamentos/{idEncaminhamento}` | JSON `{statusEncaminhamento, dataConsulta}` | 200 EncaminhamentoResponse; 404 ausente |

Campos das respostas:

- LoginResponse: `idAutenticacao, idBeneficiario, nome, email, tipoUsuario`; gestor pode ter beneficiário/nome null; nunca inclui senha/hash. Não retorna token.
- BeneficiarioResponse: `id, idEmpresa, nome, cpf, email, dataNascimento, telefone, status, dataCadastro`.
- CheckinResponse: `idCheckin, idBeneficiario, dataCheckin, canal, nivelEstresse, qualidadeSono, qualidadeAlimentacao, humor, respostaTexto`.
- MedicacaoResponse: `idMedicacao, idBeneficiario, nomeMedicamento, dosagem, horarioPrevisto`.
- ConfirmacaoMedicacaoResponse: `idConfirmacao, idBeneficiario, idMedicacao, dataConfirmacao, statusConfirmacao`.
- ScoreResponse: `idScore, idBeneficiario, dataCalculo, valorScore, classificacaoRisco`; `fatores` aparece no recálculo e é omitido na leitura do histórico. Fatores: hipertensao, diabetes, outrasComorbidades, idade, adesaoMedicacao, checkins, dadosAdesaoSuficientes, dadosCheckinsSuficientes.
- DashboardResponse: `idEmpresa, totalBeneficiarios, beneficiariosComScore, beneficiariosSemScore, baixoRisco, medioRisco, altoRisco, scoreMedio, encaminhamentosPendentes`.
- GestaoBeneficiarioResponse: `idBeneficiario, idEmpresa, nome, email, statusAtivo, valorScore, classificacaoRisco, dataCalculoScore`.
- ProfissionalResponse: `idProfissional, nome, crm, especialidade`.
- EncaminhamentoResponse: `idEncaminhamento, idBeneficiario, nomeBeneficiario, idProfissional, nomeProfissional, especialidade, dataEncaminhamento, dataConsulta, statusEncaminhamento`.

Regras relevantes de integração preservadas:

- Datas de calendário em `yyyy-MM-dd`; data/hora em ISO local, sem offset. Consulta de encaminhamento é truncada a segundos. AGENDADO exige `dataConsulta`; outros status aceitam null. O PATCH atual substitui a data, inclusive por null quando omitida; não é JSON Merge Patch.
- Status de encaminhamento: PENDENTE, AGENDADO, REALIZADO, CANCELADO. Ausência de `idEmpresa` significa visão global; parâmetro vazio/inválido é 400 e empresa inexistente é 404.
- Check-in admite campos opcionais; estresse, quando informado, é inteiro entre 1 e 5. Textos têm limites em bytes UTF-8: sono 20, alimentação 30, humor 20, observação 500. JSON vazio é aceito pelo contrato atual.
- Medicação exige nome não branco, até 100 bytes; dosagem até 50 e horário até 5, opcionais. Não há validação semântica de horário HH:mm no contrato atual; não foi inventada nesta auditoria.
- Cadastro exige todos os sete campos, rejeita extras; CPF com 11 dígitos; telefone com 10–15 dígitos; e-mail válido; nascimento não futuro; senha não branca até 72 bytes UTF-8. Regras completas em CP10. Selecionar empresa não comprova vínculo corporativo.
- Não existe GET REST de check-ins recentes: essa leitura é usada pelo assistente. Nenhuma rota foi criada para suprir uma suposição sobre o frontend.
- CORS permite somente `/api/**`, métodos GET, POST, PUT, DELETE, PATCH, OPTIONS e headers Content-Type/Accept. Permitir método no CORS não cria endpoint PUT/DELETE. Cookies cross-origin não foram habilitados.
- Não repetir automaticamente cadastros, check-ins, confirmações, encaminhamentos ou recálculos após timeout: não há chave de idempotência e um commit pode ter ocorrido.

## 6. Banco/persistência

Os caminhos REST/assistente efetivamente utilizados usam PreparedStatement, parâmetros vinculados e try-with-resources para Connection/Statement/ResultSet. Trechos dinâmicos de SQL de gestão/encaminhamento são constantes selecionadas pelo código, com filtros do usuário vinculados por `?`.

Escritas atuais de cadastro, check-in, medicação, confirmação, score e encaminhamento usam transação explícita, commit e rollback; IDs retornam por nomes de colunas geradas. Cadastro insere beneficiário e autenticação na mesma conexão. Confirmação verifica a propriedade medicação–beneficiário com FOR UPDATE na mesma transação. As constraints de unicidade conhecidas do cadastro são convertidas em 409 após rollback.

Consultas de score e gestão escolhem o último score por data e ID, evitando duplicar beneficiários no dashboard. Nulls opcionais de adesão/check-in/data de consulta são tratados nos caminhos correspondentes. Datas obrigatórias de beneficiário e score dependem das constraints NOT NULL documentadas nas fixtures; elas não foram certificadas no Oracle real.

Não foi realizada conexão ao Oracle real. H2 MODE=Oracle e mocks validam contratos, rollback, recursos e boa parte das queries, mas não certificam Oracle JDBC, permissões, identities, constraints reais, rede, TLS/wallet ou versão do servidor. As queries com FETCH FIRST e identities pressupõem Oracle compatível com esses recursos (12c ou posterior). Não foram executados scripts sobre banco externo.

Pendências operacionais: definir limites de conexão/leitura JDBC e capacidade de conexões para a hospedagem, validar fuso do banco/JVM (score usa America/Sao_Paulo; outros timestamps vêm do banco) e conferir schema oficial. Não há pool ou timeouts JDBC explícitos na aplicação. A API inicia com configuração Oracle sintaticamente válida sem abrir conexão; isso não comprova banco saudável.

Métodos legados de `GenericDAO` em BeneficiarioDAO/CheckinDAO/MedicacaoDAO/EmpresaDAO ainda capturam SQLException e imprimem mensagens. Não são chamados pelos fluxos REST/assistente atuais e não foram refatorados. Não os reutilizar em novos fluxos sem revisar propagação de erros/logs. O DDL oficial completo é referenciado na documentação histórica por um anexo local; não há migration de produção neste repositório. Fixtures H2 são somente de teste.

## 7. Telegram/Gemini

- Telegram: desabilitado por padrão; enabled sem token válido não inicia polling e emite aviso sem valor. Polling em worker separado, backoff limitado, tratamento de 429/retry_after, interrupção e encerramento. Conexão HTTP limitada a 10 s; polling e envio têm timeout; redirects desabilitados. Credencial rejeitada interrompe o polling sem derrubar a API.
- Gemini: desabilitado por padrão; enabled sem chave/configuração válida mantém fallback. Não faz chamada remota durante criação do bean. Timeout de conexão 5 s e chamada padrão 12 s (configurável de 1–30); cooldown para falhas/rate limit e suspensão após 401/403. Não segue redirects e envia chave em header.
- Saída da IA é validada como JSON estrito, com intenções/IDs confrontados pelo Java; explicações usam vocabulário aprovado. Gemini não executa SQL nem calcula score. Respostas do questionário ativo não são enviadas ao modelo.
- Testes existentes cobrem indisponibilidade, configurações ausentes, erros de transporte, sanitização, cooldown/backoff, comandos e jornadas. Não houve chamada real a Telegram/Gemini, envio de mensagem, uso de chave real ou validação de disponibilidade do modelo configurado.
- Limitações para deploy: um único poller por token; offset e conversas em memória, sem durabilidade entre reinícios/réplicas. Telegram não autentica o vínculo chat–beneficiário. Integrações devem permanecer desabilitadas em produção pública até tratar autorização e configuração operacional.

## 8. Deploy e execução

Requisitos atendidos: JAR executável com dependências, Java 21, configuração por ambiente, PORT com fallback 8080, origens CORS explícitas configuráveis, integrações opcionais desabilitadas por padrão e respostas REST sem stack trace/detalhes SQL. Nenhum caminho absoluto local é necessário pelo código de produção.

Configurar externamente no ambiente do processo:

| Variável | Uso |
| --- | --- |
| ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD | Obrigatórias; valores privados administrados fora do Git. |
| PORT | Porta HTTP; padrão 8080. |
| CORS_ALLOWED_ORIGINS | Origens exatas do APP separadas por vírgula, com esquema e porta quando aplicável, sem barra final/caminho. Substitui integralmente os defaults locais; não acrescenta a eles. Não configurar domínio do site comercial. |
| TELEGRAM_BOT_ENABLED / TELEGRAM_BOT_TOKEN | Habilitação explícita/token externo. Padrão false/vazio. |
| TELEGRAM_BOT_POLL_TIMEOUT_SECONDS | Padrão 25, limitado a 1–50. |
| GEMINI_ENABLED / GEMINI_API_KEY | Habilitação explícita/chave externa. Padrão false/vazio. |
| GEMINI_MODEL / GEMINI_TIMEOUT_SECONDS | Modelo da conta e timeout; padrão atual gemini-3.5-flash-lite / 12 s. Validar disponibilidade com o provedor antes de habilitar. |

Na máquina de build com JDK 21 e Maven: `mvn clean verify`. O modo `-o` usado nesta auditoria depende do cache Maven já disponível e não deve ser exigido no primeiro build de outra máquina. Para executar: `java -jar target/Daiji-Sprint04-1.0-SNAPSHOT.jar`. Arquivos `.env` não são carregados automaticamente.

Não é obrigatório usar Docker para hospedar esse JAR; por isso não foram adicionados Dockerfile/.dockerignore ou uma pipeline sem plataforma definida. Caso se adote container posteriormente, limitar explicitamente o contexto e excluir `.idea`, `.env*` e demais arquivos privados.

Pendentes antes de produção: autenticação/autorização e isolamento por empresa/beneficiário, TLS/reverse proxy, origem real do APP, secrets da hospedagem, conectividade/schema/permissões Oracle, timeouts/capacidade JDBC, política de logs/rate limiting, monitoramento e readiness. Hoje não existe endpoint de saúde; porta aberta e startup bem-sucedido indicam apenas processo HTTP ativo, não saúde do Oracle. Não foi criado endpoint novo.

Conclusão técnica: pode ser revisado/versionado e integrado localmente. Publicação com dados reais continua condicionada às pendências de segurança e operação; build verde não significa aprovação de produção.

## 9. Segurança do repositório

Varredura dos arquivos atuais de código/configuração/documentação e arquivos locais, com saída mascarada para candidatos a segredos. Nenhum secret real foi identificado nos arquivos versionados inspecionados; senhas de exemplo em testes pertencem às fixtures. Isso não constitui certificação automática de todos os formatos de secret nem análise de vulnerabilidades de dependências/CVEs.

Encontrado em `.idea/workspace.xml`: credenciais/configuração Oracle, token Telegram e chave Gemini locais. O arquivo está ignorado e não está no índice Git; a consulta de histórico desses caminhos de configuração local não retornou commits. Nenhum valor foi reproduzido ou modificado. Manter fora de Git, imagens, anexos e backups compartilhados; preferir configuração privada da IDE/gestor de segredos. Se houver exposição anterior fora deste repositório, rotacionar as credenciais no respectivo provedor.

`.gitignore` já protege `.idea/`, `.env`, `.env.*`, properties locais e `target/`. `application.properties` contém referências a variáveis, sem credenciais literais. Artefatos de build não devem ser commitados. Não houve revisão exaustiva de todos os blobs históricos do Git.

## 10. Diff e 11. Pendências manuais

Todos os arquivos alterados/criados estão na tabela da seção 2. Revisar `git diff` e os quatro arquivos novos (três testes e este relatório), pois arquivos não rastreados não aparecem no diff comum. Nenhuma operação de staging/versionamento foi realizada.

Antes de publicar: resolver os bloqueios de autorização; provisionar e validar Oracle em ambiente controlado; configurar o domínio do APP e segredos no host; definir monitoramento e estratégia do poller; validar Telegram/Gemini reais com dados demonstrativos e configuração privada. O frontend precisa usar os contratos documentados, sem presumir sessão/token no retorno do login.

Referências de configuração consultadas: [CORS do Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html) e [servidor embutido do Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html). As evidências específicas desta auditoria vêm do código e das execuções locais.
