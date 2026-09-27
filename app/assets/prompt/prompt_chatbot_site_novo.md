Quero que você implemente um chatbot funcional baseado em regras na landing page DAIJI que está aberta neste workspace (versão redesenhada, com 8 páginas).

## 0. CONTEXTO JÁ CONFIRMADO DO PROJETO (não precisa redescobrir, mas valide)

- Existem 8 páginas: index.html, planos.html, especialidades.html, seguranca.html, sobre.html, contato.html, como-funciona.html, estrategia.html. TODAS têm o widget flutuante.
- O widget flutuante usa sempre os mesmos IDs: `chatFab`, `chatPanel`, `chatClose`, `chatForm`, `chatInput`, `chatBody`.
- A lógica está centralizada em `js/main.js`, na função `sendMockReply()` — é essa função (e o listener do `chatForm`) que você vai substituir pela lógica de intenções.
- `contato.html` tem uma SEGUNDA interface, inline, com IDs próprios: `chatBodyInline`, `chatFormInline`, `chatInputInline`. Hoje ela tem um `<script>` solto e duplicado dentro do próprio `contato.html`, com resposta hardcoded diferente da do widget flutuante ("Protótipo de demonstração — conecte este formulário ao agente conversacional real (Telegram Bot API)..."). Unifique os dois para chamarem a MESMA função de processamento, eliminando essa duplicação.
- IMPORTANTE — mudou desde a versão anterior: este site NÃO tem mais nenhum e-mail ou telefone de contato publicado. O que existe é um FORMULÁRIO de contato na própria contato.html (campos de nome/e-mail/mensagem). Portanto, o fallback e o intent de "contato" devem orientar a pessoa a preencher o formulário na página de contato, nunca inventar um e-mail ou telefone.
- Ajuste de texto necessário: o cabeçalho do painel hoje diz "Daiji no Telegram" / "Protótipo de demonstração", e em contato.html há um parágrafo dizendo que o painel "simula o check-in diário, os lembretes de medicação e o roteamento de dúvidas que a Daiji faz pelo Telegram". Isso é resquício de um rascunho anterior — o widget deve se apresentar como o "Assistente Daiji", um assistente de perguntas frequentes sobre a plataforma (não uma simulação de check-in/medicação do Telegram). Atualize esse cabeçalho e esse parágrafo para refletir a função real: um assistente de FAQ institucional do site.
- Textos de placeholder que precisam ser removidos ao final (aparecem em mais de uma página, com variações): "Espaço reservado — integre seu chatbot em js/main.js", "Protótipo — integre o agente conversacional real em js/main.js", a bolha inicial dizendo "Este é um espaço reservado para o chatbot...", e qualquer variação equivalente.
- Os chips de perguntas rápidas (seção 8 abaixo) NÃO existem ainda no HTML — você vai criá-los, pequenos, combinando com o visual atual.

## 1. PRIMEIRO: ANALISE O PROJETO

Antes de modificar qualquer arquivo:

1. Leia a estrutura completa do projeto (as 8 páginas, css, js).
2. Analise o JavaScript responsável pelo chatbot (`js/main.js` e o `<script>` inline de `contato.html`).
3. Leia o conteúdo de TODAS as páginas, incluindo as duas novas (como-funciona.html e estrategia.html), para descobrir quais informações reais podem ser usadas nas respostas (planos e preços reais, especialidades reais, texto real de segurança/LGPD, texto real de "o que é a Daiji", como funciona o produto, estratégia de captação/lead).
4. Confirme que não existe e-mail nem telefone publicado — só o formulário em contato.html.

Depois da análise, implemente diretamente as alterações.

IMPORTANTE: preserve ao máximo a estrutura existente.

---

# 2. OBJETIVO

Transformar o chatbot visual já existente (flutuante, nas 8 páginas, + inline em contato.html) em UM ÚNICO chatbot funcional baseado em regras, perguntas frequentes e identificação simples de intenções, compartilhado pelas duas interfaces.

NÃO utilizar IA generativa (Gemini, OpenAI, ChatGPT API, Hugging Face ou qualquer API externa), nem Node.js, Express, backend, banco de dados, API Key ou servidor adicional.

Toda a funcionalidade deve rodar no navegador com HTML + CSS + JavaScript apenas. O projeto deve continuar podendo ser aberto/executado como landing page front-end comum.

---

# 3. NÃO REFAÇA O CHATBOT VISUAL

Reutilize o botão flutuante, a janela do chat, cabeçalho, área de mensagens, input, botão de envio, animações e estilos existentes, e o bloco inline de contato.html — em todas as 8 páginas.

A única peça de UI nova permitida são os chips de perguntas rápidas (seção 8).

Não altere desnecessariamente header, hero, cards, footer, textos institucionais, identidade visual ou responsividade existente — exceto o ajuste de texto do cabeçalho do chat e do parágrafo em contato.html mencionados na seção 0.

---

# 4. FUNCIONAMENTO GERAL

Usuário digita uma mensagem (widget flutuante OU inline de contato.html — mesmo fluxo)
→ normaliza a mensagem
→ detecta a intenção
→ retorna a resposta predefinida correspondente.

Não dependa de frases exatamente iguais — use correspondência por palavras-chave.

---

# 5. ESTRUTURA DOS INTENTS

Organize as respostas em uma estrutura centralizada em `js/main.js` (array de objetos `{id, keywords, exclude?, response}`), evitando cadeias grandes de if/else.

---

# 6. NORMALIZAÇÃO DAS PERGUNTAS

Antes de comparar: minúsculas, remover acentos, remover pontuação repetida (`???`, `!!!`), remover espaços duplicados, trim(). Trate variações como "e-mail"/"email" como equivalentes.

---

# 7. ASSUNTOS QUE O CHATBOT DEVE CONHECER

Leia TODAS as 8 páginas antes de escrever as respostas. Crie intents para:

### Saudação
oi, olá, ola, bom dia, boa tarde, boa noite, hey, e aí, eai → resposta amigável explicando que é o Assistente Daiji.

### O que é a DAIJI?
"o que é a daiji?", "o que vocês fazem?", "para que serve a daiji?", "como funciona a daiji?", "qual é a proposta da daiji?" → usar a descrição real de index.html/sobre.html.

### Como funciona (produto)
"como funciona", "como funciona o app", "como usar", "passo a passo" → usar o conteúdo real de como-funciona.html.

### Risk Score
"risk score", "score", "pontuação", "risco", "avaliação de risco", "meu score", "índice de bem-estar" → usar apenas o que está descrito no projeto (index.html). Não inventar algoritmos ou métricas.

### Check-in
"checkin", "check-in", "check in", "check-in diário", "acompanhamento diário" → conforme conteúdo real do site.

### Planos
"plano", "planos", "preço", "preços", "valor", "valores", "mensalidade", "quanto custa", "assinatura", "contratar", "qual plano" → usar exclusivamente os planos/valores reais de planos.html.

### Especialidades
"especialidade", "especialidades", "médicos", "profissionais", "atendimento", "consultas", "quais especialidades", "quais médicos" → usar especialidades.html.

### Segurança
"segurança", "meus dados", "dados pessoais", "privacidade", "lgpd", "meus dados estão seguros?" → usar seguranca.html. Não inventar certificações ou padrões não mencionados.

### Estratégia / captação / lead / empresas
"empresa", "empresas", "funcionário", "rh", "recursos humanos", "plano gratuito", "freemium", "lead", "como entrar sem plano unimed" → usar o conteúdo real de estrategia.html (modelo B2B2C, freemium, como funciona a captação).

### Contato
"contato", "falar com vocês", "falar com atendente", "atendimento", "suporte", "ajuda", "quero falar com alguém", "telefone", "email", "e-mail" → orientar a preencher o FORMULÁRIO na página de contato (contato.html). Não existe e-mail nem telefone publicado — nunca invente um.

### Agradecimento
obrigado, obrigada, valeu, agradeço, muito obrigado, vlw → resposta curta e amigável.

### Despedida
tchau, até mais, ate mais, falou, até logo → resposta breve.

---

# 8. PERGUNTAS RÁPIDAS (elemento novo de UI)

Não existe ainda — crie chips pequenos, combinando com o chat-panel existente. Sugestões (ajuste a redação conforme o conteúdo real encontrado):

[ O que é a DAIJI? ]
[ Como funciona o Risk Score? ]
[ Quais são os planos? ]
[ Quais especialidades estão disponíveis? ]
[ Meus dados estão seguros? ]
[ Como entrar em contato? ]

Ao clicar: a pergunta aparece como mensagem do usuário, processada pela MESMA função usada para digitação. Adicione os chips no painel flutuante (todas as 8 páginas) e no bloco inline de contato.html.

---

# 9. RESPOSTA DESCONHECIDA

Quando não identificar a intenção:

"Essa dúvida é mais específica e não consigo respondê-la por aqui. Para receber mais informações, preencha o formulário na nossa página de contato e a equipe Daiji retorna para você."

Não invente e-mail, telefone ou qualquer outro canal.

---

# 10. PERGUNTAS MÉDICAS

Detectar termos como: "estou com dor", "estou sentindo", "sintomas", "qual doença", "tenho doença", "diagnóstico", "qual remédio", "medicamento", "posso tomar", "dose", "tratamento", "como tratar", "o que eu tenho".

Resposta: "O Assistente Daiji fornece apenas informações sobre a plataforma e não realiza diagnósticos ou recomendações médicas. Para avaliar sintomas, medicamentos ou tratamentos, procure um profissional de saúde."

Não tente responder a parte médica.

---

# 11. SITUAÇÕES DE POSSÍVEL EMERGÊNCIA

Detectar: "não consigo respirar", "falta de ar intensa", "desmaiei", "desmaiando", "dor forte no peito", "emergência", "socorro", "sangramento intenso".

Nesses casos, NÃO usar fallback nem resposta médica padrão — retornar orientação curta para procurar atendimento de emergência (SAMU 192) imediatamente. Não tentar diagnosticar a causa.

---

# 12. PRIORIDADE DAS REGRAS

Ordem: 1) emergência; 2) pergunta médica individual; 3) intents da Daiji; 4) saudação/agradecimento/despedida; 5) fallback. Emergência sempre vem primeiro.

---

# 13. EVITE FALSOS POSITIVOS

Use pontuação simples por keyword (frases completas pesam mais que palavras soltas) e um threshold mínimo. Exemplo: "qual o melhor plano de internet" contém "plano" mas não deve disparar o intent de planos da Daiji.

---

# 14. SIMULAÇÃO DE DIGITAÇÃO

Mostrar a mensagem do usuário → indicador "Digitando..." → pequeno intervalo (setTimeout) → remover indicador → mostrar resposta. Sem API ou processamento assíncrono externo.

---

# 15. HISTÓRICO

Preservar mensagens enquanto a página estiver aberta. sessionStorage é opcional; banco de dados e login não são necessários.

---

# 16. UNIFICAÇÃO FLUTUANTE + INLINE (crítico)

`contato.html` tem hoje um `<script>` inline duplicado com resposta hardcoded própria. Elimine essa duplicação: toda a lógica de intents fica em `js/main.js`; o script inline de `contato.html` só conecta `chatBodyInline`/`chatFormInline`/`chatInputInline` à MESMA função de processamento do widget flutuante. Mesmos intents, keywords, respostas, proteções e fallback para os dois.

---

# 17. ORGANIZAÇÃO DO JAVASCRIPT

Separe responsabilidades: normalizeText(), detectIntent(), processMessage(), getResponse(), appendBubble(), showTypingIndicator(), handleQuickQuestion(). Comentários só onde ajudam de verdade.

---

# 18. FACILIDADE PARA ADICIONAR NOVAS PERGUNTAS

Nova intenção deve ser só um novo objeto no array `intents`, sem alterar a função principal.

---

# 19. PERSONALIDADE

Nome: "Assistente Daiji". Tom profissional, amigável, simples, objetivo. Evitar respostas enormes, excesso de emojis, linguagem infantil, fingir ser médico, ou afirmar ser uma IA generativa.

---

# 20. NÃO INVENTE CONTEÚDO

Toda informação sobre Daiji, planos, preços, especialidades, Risk Score, segurança, estratégia/lead e contato deve vir dos arquivos reais do projeto. Na dúvida, prefira resposta genérica ou encaminhar para o formulário de contato.

---

# 21. LIMPEZA DE TEXTOS DE PLACEHOLDER E AJUSTE DE COPY (importante)

Remova/atualize, em todas as páginas onde aparecerem:
- "Espaço reservado — integre seu chatbot em js/main.js"
- "Protótipo — integre o agente conversacional real em js/main.js"
- A bolha inicial "Este é um espaço reservado para o chatbot..."
- O cabeçalho "Daiji no Telegram" / "Protótipo de demonstração" → trocar para "Assistente Daiji" / "Online"
- O parágrafo em contato.html que descreve o painel como simulação de check-in/medicação do Telegram → ajustar para descrever corretamente um assistente de FAQ da plataforma
- A resposta hardcoded do script inline de contato.html sobre "conectar ao Telegram Bot API"

Nenhum texto de placeholder ou copy desatualizada pode continuar visível depois da implementação.

---

# 22. TESTES

Testar nas duas interfaces (flutuante em todas as páginas + inline de contato.html):

1. "oi" 2. "o que é a daiji?" 3. "como funciona o score?" 4. "quanto custa?" 5. "quais são os planos?" 6. "quais especialidades vocês têm?" 7. "meus dados estão seguros?" 8. "como entro em contato?" 9. "obrigado" 10. "tchau"

Também:
- "qual remédio devo tomar para dor nos rins?" → sem medicamento, orientar profissional.
- "estou com uma dor muito forte no peito e falta de ar" → orientação de emergência.
- "vocês fazem cirurgia?" (não documentado) → fallback, encaminhar para o formulário de contato, sem inventar.
- "quem ganhou a copa?" (fora de contexto) → explicar que só responde sobre a Daiji.

---

# 23. RESPONSIVIDADE

Garantir que chips e novas mensagens não quebrem em desktop, telas menores e mobile, nas 8 páginas.

---

# 24. ESCOPO

Principais mudanças em: js/main.js (intents + lógica); contato.html (remover script duplicado, remover placeholders, ajustar parágrafo); as 8 páginas HTML (chips + ajuste do cabeçalho do chat); css/style.css (chips + indicador de digitação). Não altere conteúdo/layout de outras partes sem necessidade.

---

# 25. RESULTADO FINAL

Apresente relatório com: arquivos modificados; o que foi implementado (incluindo como resolveu a duplicação flutuante/inline e o ajuste de copy do cabeçalho); onde está a base de intents; exemplo de como adicionar uma nova pergunta; testes realizados (nas duas interfaces) e quais intents foram acionados; confirmação de que não há API externa/key/Gemini/OpenAI/backend/Node.js; como executar a landing page.

Antes de finalizar, revise se: nenhum texto de placeholder ficou visível em nenhuma das 8 páginas; o script duplicado de contato.html foi unificado, não só ocultado; o cabeçalho do chat e o parágrafo de contato.html não mencionam mais "simulação do Telegram".
