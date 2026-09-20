# CP10 — Cadastro de Beneficiário

## Escopo e fonte

Cadastro público vinculado a uma EMPRESA existente, selecionada no frontend. O backend não cria empresa, não presume ID 1 e não altera schema.
Limitação aceita do MVP: a seleção não comprova vínculo corporativo; convite, autorização e validação com RH não existem neste CP.

Fonte exclusiva: script oficial completo do MVP final, anexo
`C:/Users/felip/.codex/attachments/25391e3f-f264-4922-9e0f-cc157273095d/pasted-text.txt`.
SHA-256: `67B2179A9B46F540B4DA4F70C93C9C9A61C67F21BC6ED89ED24250E84FD77FC5`.
Mini-script legado desconsiderado. Nenhum script SQL foi alterado.

## Empresas para o select

Não havia endpoint apropriado. Foi criado `GET /api/empresas`, somente leitura:

HTTP 200:
```json
[
  {
    "idEmpresa": 1,
    "nome": "Empresa Exemplo"
  }
]
```

`nome` corresponde à coluna real `EMPRESA.razao_social`; `idEmpresa` a `id_empresa`.
Ordenação por razão social e ID. Sem registros retorna `[]`. Não expõe CNPJ ou data de cadastro.
Falha SQL usa o tratamento existente: HTTP 500, `{"mensagem":"Não foi possível consultar o banco de dados."}`.

## Cadastro

`POST /api/auth/cadastro`, `Content-Type: application/json`.

Request:
```json
{
  "idEmpresa": 1,
  "nome": "Maria da Silva",
  "cpf": "52998224725",
  "email": "maria@example.com",
  "dataNascimento": "1996-05-21",
  "telefoneWhatsapp": "11950000000",
  "senha": "SenhaExemplo@123"
}
```

Todos os campos são obrigatórios. O corpo deve ser um objeto JSON com exatamente esses campos.
IDs, perfil, hash, status, data de cadastro e quaisquer campos adicionais são rejeitados.
Campos textuais não aceitam coerção de números/booleanos; idEmpresa não aceita string nem número fracionário.

HTTP 201, somente após COMMIT (IDs ilustrativos):
```json
{
  "idAutenticacao": 42,
  "idBeneficiario": 37,
  "nome": "Maria da Silva",
  "email": "maria@example.com",
  "tipoUsuario": "BENEFICIARIO"
}
```

Reutiliza LoginResponse, sem alterar seu contrato. Não retorna senha/hash nem gera token.

| HTTP | Situação | Corpo exato |
|---|---|---|
| 400 | Dados/corpo inválidos | `{"mensagem":"Dados de cadastro inválidos."}` |
| 400 | Empresa inexistente | `{"mensagem":"Empresa informada não encontrada."}` |
| 409 | CPF/e-mail em conflito | `{"mensagem":"CPF ou e-mail já cadastrado."}` |
| 500 | Falha interna/persistência | `{"mensagem":"Não foi possível concluir o cadastro."}` |

O tratamento de erro novo é restrito ao CadastroController. Não expõe SQL, constraints, exceções, stack trace ou credenciais.

## Validações

- idEmpresa: inteiro JSON positivo no intervalo de Integer; empresa existente.
- nome: não branco, até 150 bytes UTF-8.
- CPF: exatamente 11 dígitos ASCII, sem pontuação; sem algoritmo de dígitos verificadores.
- e-mail: até 150 bytes UTF-8; formato ASCII de endereço comum, partes separadas por @, domínio com ponto, sem espaços, pontos consecutivos no local ou hífen nas extremidades dos rótulos do domínio. Não suporta todas as formas especiais de endereços RFC, como local entre aspas.
- E-mail preservado exatamente: sem conversão para minúsculas ou trim. O login permanece com comparação direta. Não se promete unicidade sem distinção de caixa.
- nascimento: string ISO yyyy-MM-dd, data válida, ano 0001–9999, não futura conforme data local do backend.
- telefoneWhatsapp: de 10 a 15 dígitos ASCII, sem espaços, sinal + ou pontuação. Aceita número nacional ou com código do país dentro desse limite; não verifica existência do telefone.
- senha: não branca, até 72 bytes UTF-8. Espaços são preservados; não há truncamento.
- Limites textuais em bytes são conservadores para VARCHAR2, cujo script não explicita BYTE/CHAR.

## Persistência e atomicidade

CadastroService valida o request e gera BCrypt antes de abrir a conexão.
CadastroDAO usa uma única Connection, obtida por ConnectionFactory:

1. setAutoCommit(false).
2. SELECT da empresa com FOR UPDATE na mesma conexão; ausência gera 400.
3. INSERT BENEFICIARIO e recuperação de ID_BENEFICIARIO via getGeneratedKeys.
4. INSERT AUTENTICACAO com esse ID, mesmo e-mail, hash e tipo fixo BENEFICIARIO.
5. Recuperação de ID_AUTENTICACAO via getGeneratedKeys.
6. COMMIT e retorno do resultado.

O driver recebe explicitamente os nomes das colunas geradas, compatível com o padrão JDBC Oracle já usado no projeto.
Cada INSERT deve afetar uma linha; chaves ausentes, nulas, não positivas ou fora do intervalo de Integer causam falha.
Identity e data_cadastro ficam a cargo do banco. status_ativo e ativo são omitidos e usam os defaults 'S' oficiais.

SQLException ou RuntimeException dentro da transação provoca rollback antes da propagação do erro.
Falha do rollback é tratada como erro interno, nunca como sucesso ou conflito comum.
Recursos JDBC são fechados por try-with-resources. IDs identity podem ter lacunas após rollback.
Não depende de @Transactional nem reutiliza métodos legados que abrem conexões separadas e capturam erros.

FOR UPDATE impede que a empresa desapareça entre sua validação e os INSERTs; cadastros na mesma empresa podem aguardar esse bloqueio até o término da transação.

## Duplicidades

As constraints existentes são a autoridade, inclusive sob concorrência:
uk_beneficiario_cpf, uk_beneficiario_email e uk_autenticacao_email.
Violação Oracle ORA-00001 identificando uma dessas constraints gera 409 após rollback.
O teste H2 usa SQLState 23505 com a mesma identificação de constraint.
Outras falhas, inclusive uma PK inesperada, não são convertidas genericamente em 409.
E-mail já usado por GESTOR também impede novo cadastro. Não se tenta vincular automaticamente uma conta existente.

## BCrypt e login

Usa BCryptPasswordEncoder.encode, compatível com o matches do AuthService existente.
CadastroRequest omite dados em toString; implementação não registra senha/hash em logs.
Após o 201, o cliente pode enviar ao endpoint existente:

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "maria@example.com",
  "senha": "SenhaExemplo@123"
}
```

A resposta 200 utiliza o mesmo formato acima. AuthController, AuthService, AutenticacaoDAO e LoginResponse não foram alterados.

## Cadastro e onboarding

CP10 cria somente BENEFICIARIO e AUTENTICACAO vinculados à EMPRESA existente.
Doenças, medicações, atividade física e demais dados clínicos pertencem a onboarding posterior.
Não cria score, check-in, confirmação, comorbidade, medicação ou qualquer registro clínico.
Não altera Telegram, Gemini, dashboard, encaminhamentos ou regras CP01–CP09.

## Classes

Criadas:
- controller: CadastroController, CadastroExceptionHandler, EmpresaController.
- service: CadastroService, CadastroException.
- dao: CadastroDAO, EmpresaOpcaoDAO.
- dto: CadastroRequest, EmpresaOpcaoResponse.
- testes: CadastroApiTest, CadastroDAOTest.

Nenhuma classe existente foi alterada. README recebeu somente a referência a este documento.
Não foram adicionadas dependências.

## Testes

CadastroApiTest: 69 casos com MockMvc, beans reais e H2 em memória, substituindo somente ConnectionFactory.
Inclui persistência, IDs, defaults, BCrypt, login imediato, limite UTF-8 com espaços preservados,
empresa inexistente, entradas inválidas/nulas/ausentes, campos extras, duplicidades,
falha real em cada INSERT, concorrência e consulta mínima de empresas.
A fixture é definida no código de teste a partir das três tabelas oficiais, sem alterar scripts SQL.

CadastroDAOTest: 14 casos com mocks JDBC verificando única conexão, ordem dos INSERTs/chaves/commit,
fechamento de recursos, falhas nos dois INSERTs, consulta, chaves, commit e rollback,
classificação de constraints Oracle e rollback de RuntimeException.

Regressão exigida: 591 testes anteriores mais 83 testes CP10 (674 no total).
Execução completa: `mvn -o verify`, Java 21, Maven já instalado, integrações externas desabilitadas.
Resultados efetivos ficam nos relatórios Surefire e no relatório final da execução.
Nenhum teste conecta Oracle, Telegram ou Gemini reais.
