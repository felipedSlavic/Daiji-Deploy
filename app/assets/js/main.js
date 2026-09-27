/* ==========================================================================
   DAIJI — shared behaviors
   - Nav goes solid on scroll
   - Light/dark theme toggle (persisted in localStorage)
   - Scroll-reveal for .fx-in elements
   - Assistente Daiji: chatbot baseado em regras (intents por palavra-chave),
     compartilhado pelo widget flutuante (todas as páginas) e pelo bloco
     inline de contato.html
   ========================================================================== */

/* ---------------------------------------------------------------------- */
/* Assistente Daiji — base de conhecimento e motor de intents             */
/* ---------------------------------------------------------------------- */

const FALLBACK_RESPONSE = 'Essa dúvida é mais específica e não consigo respondê-la por aqui. Para receber mais informações, preencha o formulário na nossa página de contato e a equipe Daiji retorna para você.';

const intents = [

  /* 1) emergência — sempre verificado primeiro */
  {
    id: 'emergencia',
    group: 'emergencia',
    keywords: [
      'não consigo respirar', 'falta de ar intensa', 'falta de ar',
      'desmaiei', 'desmaiando', 'dor forte no peito', 'forte no peito',
      'dor no peito', 'emergência', 'socorro', 'sangramento intenso', 'sangramento'
    ],
    response: 'Isso pode ser uma emergência médica. Ligue agora para o SAMU (192) ou procure o pronto-socorro mais próximo. O Assistente Daiji não presta atendimento de emergência.'
  },

  /* 2) pergunta médica individual */
  {
    id: 'medico',
    group: 'medico',
    keywords: [
      'estou com dor', 'estou sentindo', 'sintomas', 'qual doença', 'tenho doença',
      'diagnóstico', 'qual remédio', 'medicamento', 'posso tomar', 'dose',
      'tratamento', 'como tratar', 'o que eu tenho'
    ],
    response: 'O Assistente Daiji fornece apenas informações sobre a plataforma e não realiza diagnósticos ou recomendações médicas. Para avaliar sintomas, medicamentos ou tratamentos, procure um profissional de saúde.'
  },

  /* 3) intents da Daiji */
  {
    id: 'o_que_e_daiji',
    group: 'core',
    keywords: [
      'o que é a daiji', 'o que é daiji', 'o que vocês fazem', 'para que serve a daiji',
      'como funciona a daiji', 'qual é a proposta da daiji', 'quem é a daiji'
    ],
    response: 'A Daiji é a camada de cuidado preditivo da Unimed Nacional: acompanha sua rotina pelo Telegram e pelo app, identifica mudanças de saúde antes que virem emergência e conecta você à rede de especialistas certa no momento certo. É um projeto do FIAP Challenge em parceria com a Unimed Nacional — だいじ significa "aquilo que importa e merece cuidado".'
  },
  {
    id: 'como_funciona',
    group: 'core',
    keywords: [
      'como funciona', 'como funciona o app', 'como usar', 'passo a passo',
      'como é o dia a dia', 'dia a dia com a daiji'
    ],
    response: 'No dia a dia, a Daiji acontece em pequenos momentos pelo Telegram: um check-in rápido de manhã, um lembrete de medicação ao meio-dia, dúvidas respondidas e roteadas à tarde e, quando há uma mudança relevante, uma sugestão de consulta com horários já disponíveis à noite. A equipe de saúde sempre decide a parte clínica — a Daiji só organiza os sinais.'
  },
  {
    id: 'risk_score',
    group: 'core',
    keywords: [
      'risk score', 'score', 'pontuação', 'risco', 'avaliação de risco', 'meu score',
      'índice de bem-estar', 'como funciona o score', 'como funciona o risk score',
      'o que é o score', 'o que é o risk score'
    ],
    response: 'O Risk Score é um número de 0 a 100, recalculado continuamente a partir dos seus check-ins, adesão e histórico clínico. Ele não é um diagnóstico — indica a prioridade do seu próximo cuidado. Como exemplo ilustrativo: abaixo de 30 é só acompanhamento de rotina, entre 30 e 60 há reforço de check-ins, e acima de 60 vem a recomendação de consulta com a especialidade indicada. Hoje o piloto está ativo para a linha de cuidado renal (Nefrologia).'
  },
  {
    id: 'checkin',
    group: 'core',
    keywords: [
      'checkin', 'check-in', 'check in', 'check-in diário', 'acompanhamento diário',
      'como funciona o checkin', 'como funciona o check-in'
    ],
    response: 'O check-in diário é uma pergunta simples que a Daiji manda todo dia pelo Telegram, como "Como você está hoje?". Você responde por áudio, texto ou emoji, sem precisar abrir outro app — e isso já ajuda a identificar mudanças de humor, sono, disposição e adesão à medicação ao longo do tempo.'
  },
  {
    id: 'planos',
    group: 'core',
    keywords: [
      'plano', 'planos', 'preço', 'preços', 'valor', 'valores', 'mensalidade',
      'quanto custa', 'assinatura', 'contratar', 'qual plano'
    ],
    exclude: ['internet', 'celular', 'wifi', 'dados móveis', 'telefonia', 'tv a cabo', 'banda larga'],
    response: 'A Daiji tem dois planos: o Daiji Essencial, gratuito e sem cartão de crédito, com check-ins, busca na rede de especialistas, conteúdo educativo e recompensas leves; e o Daiji Coordenado, liberado quando sua empresa ou plano Unimed já é parceiro, com score de risco monitorado, encaminhamento coordenado com a rede credenciada, prioridade no agendamento, plantão 24h e compartilhamento com a família. Não há valores em reais divulgados nesta versão do site.'
  },
  {
    id: 'especialidades',
    group: 'core',
    keywords: [
      'especialidade', 'especialidades', 'médicos', 'profissionais', 'atendimento',
      'consultas', 'quais especialidades', 'quais médicos'
    ],
    response: 'A rede da Daiji reúne 12 especialidades: Nefrologia, Psicologia, Nutrição, Clínico Geral, Cardiologia, Dermatologia, Pediatria, Endocrinologia, Ortopedia, Ginecologia, Oftalmologia e plantão 24h. Hoje a jornada completa de score e encaminhamento está ativa para Nefrologia; as demais especialidades já fazem parte da rede, com Saúde Mental e outras linhas entrando nas próximas fases.'
  },
  {
    id: 'seguranca',
    group: 'core',
    keywords: [
      'segurança', 'meus dados', 'dados pessoais', 'privacidade', 'lgpd',
      'meus dados estão seguros'
    ],
    response: 'Sim — a Daiji dá a cada camada só o dado que ela precisa: você tem uma experiência protegida pelas regras de privacidade e governança da Unimed; o RH da sua empresa só enxerga indicadores agregados e anônimos, nunca dados clínicos individuais; e a Unimed acompanha o risco e a jornada clínica com acesso autorizado. Os dados são protegidos com criptografia de ponta a ponta, consentimento explícito e rastreabilidade, seguindo a LGPD desde a concepção do projeto.'
  },
  {
    id: 'estrategia',
    group: 'core',
    keywords: [
      'empresa', 'empresas', 'funcionário', 'rh', 'recursos humanos',
      'plano gratuito', 'freemium', 'lead', 'como entrar sem plano unimed'
    ],
    response: 'O Daiji Essencial é gratuito para qualquer pessoa no Brasil. Dentro das praças da Unimed CNU, o lead segue direto para a contratação do plano; fora delas, é encaminhado para a Unimed local responsável pela região. O piloto atual está concentrado na Unimed CNU, usando telemedicina (parceria Dr.Online) como porta de entrada: o Essencial aquece o relacionamento, a consulta de telemedicina identifica intenção ou risco, e isso conduz ao Daiji Coordenado. Se você é RH ou quer saber se sua empresa participa, preencha o formulário na página de Contato.'
  },
  {
    id: 'contato',
    group: 'core',
    keywords: [
      'contato', 'falar com vocês', 'falar com atendente', 'atendimento', 'suporte',
      'ajuda', 'quero falar com alguém', 'telefone', 'email', 'e-mail'
    ],
    response: 'Hoje não temos e-mail ou telefone público — o caminho é preencher o formulário na página de Contato do site, com nome, e-mail e mensagem. A equipe Daiji retorna para você por lá.'
  },

  /* 4) saudação / agradecimento / despedida */
  {
    id: 'saudacao',
    group: 'social',
    keywords: ['oi', 'olá', 'ola', 'bom dia', 'boa tarde', 'boa noite', 'hey', 'e aí', 'eai'],
    response: 'Olá! 👋 Eu sou o Assistente Daiji. Posso te ajudar com dúvidas sobre a plataforma: o que é a Daiji, como funciona, planos, especialidades, segurança dos dados e como entrar em contato. O que você quer saber?'
  },
  {
    id: 'agradecimento',
    group: 'social',
    keywords: ['obrigado', 'obrigada', 'valeu', 'agradeço', 'muito obrigado', 'vlw'],
    response: 'De nada! Se surgir outra dúvida sobre a Daiji, é só chamar. 🙂'
  },
  {
    id: 'despedida',
    group: 'social',
    keywords: ['tchau', 'até mais', 'ate mais', 'falou', 'até logo'],
    response: 'Até mais! Volte sempre que precisar tirar dúvidas sobre a Daiji. 👋'
  }
];

function normalizeText(text) {
  if (!text) return '';
  let t = String(text).toLowerCase();
  t = t.normalize('NFD').replace(/[̀-ͯ]/g, ''); // remove acentos
  t = t.replace(/-/g, ' ');                                // "check-in" -> "check in"
  t = t.replace(/[^a-z0-9\s]/g, ' ');                       // remove pontuação (inclusive repetida: "???", "!!!")
  t = t.replace(/\be\s+mail\b/g, 'email');                  // "e-mail" === "email"
  t = t.replace(/\s+/g, ' ').trim();
  return t;
}

function escapeRegExp(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function containsKeyword(normalizedText, normalizedKeyword) {
  if (!normalizedKeyword) return false;
  const pattern = new RegExp('\\b' + escapeRegExp(normalizedKeyword).replace(/ /g, '\\s+') + '\\b');
  return pattern.test(normalizedText);
}

function scoreIntent(normalizedText, intent) {
  if (intent.exclude && intent.exclude.some(kw => containsKeyword(normalizedText, normalizeText(kw)))) {
    return 0;
  }
  let score = 0;
  for (const kw of intent.keywords) {
    const nkw = normalizeText(kw);
    if (containsKeyword(normalizedText, nkw)) {
      const wordCount = nkw.split(' ').filter(Boolean).length;
      score += wordCount >= 2 ? wordCount : 1; // frases completas pesam mais que palavras soltas
    }
  }
  return score;
}

function bestMatch(normalizedText, group, threshold = 1) {
  let best = null;
  let bestScore = 0;
  for (const intent of group) {
    const score = scoreIntent(normalizedText, intent);
    if (score > bestScore) {
      bestScore = score;
      best = intent;
    }
  }
  return bestScore >= threshold ? best : null;
}

/** Ordem de prioridade: 1) emergência 2) pergunta médica 3) intents da Daiji 4) social 5) fallback */
function detectIntent(normalizedText) {
  if (!normalizedText) return null;
  const order = ['emergencia', 'medico', 'core', 'social'];
  for (const group of order) {
    const match = bestMatch(normalizedText, intents.filter(i => i.group === group));
    if (match) return match;
  }
  return null;
}

function getResponse(intent) {
  return intent ? intent.response : FALLBACK_RESPONSE;
}

function processMessage(rawText) {
  const normalized = normalizeText(rawText);
  const intent = detectIntent(normalized);
  return getResponse(intent);
}

/* Para adicionar uma nova pergunta: basta incluir um novo objeto no array
   `intents` acima ({ id, group, keywords, exclude?, response }) — nenhuma
   das funções desta seção precisa ser alterada. */

document.addEventListener('DOMContentLoaded', () => {

  /* ---- theme toggle ---- */
  const root = document.documentElement;
  const THEME_KEY = 'daiji-theme';
  const saved = localStorage.getItem(THEME_KEY);
  if (saved) root.setAttribute('data-theme', saved);

  document.querySelectorAll('[data-theme-toggle]').forEach(btn => {
    btn.addEventListener('click', () => {
      const current = root.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
      const next = current === 'light' ? 'dark' : 'light';
      if (next === 'dark') root.removeAttribute('data-theme');
      else root.setAttribute('data-theme', 'light');
      localStorage.setItem(THEME_KEY, next);
    });
  });

  /* ---- nav solid on scroll ---- */
  const nav = document.getElementById('ouraNav');
  if (nav) {
    const onScroll = () => {
      if (window.scrollY > 60) nav.classList.add('solid');
      else nav.classList.remove('solid');
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* ---- scroll reveal ---- */
  const fx = document.querySelectorAll('.fx-in');
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (e.isIntersecting) { e.target.classList.add('show'); io.unobserve(e.target); }
      });
    }, { threshold: .12 });
    fx.forEach(el => io.observe(el));
  } else {
    fx.forEach(el => el.classList.add('show'));
  }

  /* ---- Assistente Daiji: UI helpers ---- */
  function appendBubble(text, who, body) {
    if (!body) return;
    const div = document.createElement('div');
    div.className = 'chat-bubble ' + who;
    div.textContent = text;
    body.appendChild(div);
    body.scrollTop = body.scrollHeight;
  }

  function showTypingIndicator(body) {
    if (!body) return null;
    const el = document.createElement('div');
    el.className = 'chat-typing';
    el.innerHTML = '<span></span><span></span><span></span>';
    body.appendChild(el);
    body.scrollTop = body.scrollHeight;
    return el;
  }

  function removeTypingIndicator(el) {
    if (el && el.parentNode) el.parentNode.removeChild(el);
  }

  function handleUserMessage(text, body) {
    appendBubble(text, 'user', body);
    const typingEl = showTypingIndicator(body);
    const delay = 550 + Math.random() * 450;
    setTimeout(() => {
      removeTypingIndicator(typingEl);
      appendBubble(processMessage(text), 'bot', body);
    }, delay);
  }

  function handleQuickQuestion(question, body) {
    handleUserMessage(question, body);
  }

  /** Liga um formulário de chat (input + área de mensagens + chips) ao motor de intents. */
  function wireChatInterface({ form, input, body, quickWrap }) {
    if (!form || !input || !body) return;

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      handleUserMessage(text, body);
    });

    if (quickWrap) {
      quickWrap.addEventListener('click', (e) => {
        const chip = e.target.closest('.chip-q');
        if (!chip) return;
        handleQuickQuestion(chip.dataset.q || chip.textContent.trim(), body);
      });
    }
  }

  /* ---- widget flutuante (presente nas 8 páginas) ---- */
  const chatFab = document.getElementById('chatFab');
  const chatPanel = document.getElementById('chatPanel');
  const chatClose = document.getElementById('chatClose');

  const toggleChat = () => chatPanel && chatPanel.classList.toggle('open');
  if (chatFab) chatFab.addEventListener('click', toggleChat);
  if (chatClose) chatClose.addEventListener('click', toggleChat);

  wireChatInterface({
    form: document.getElementById('chatForm'),
    input: document.getElementById('chatInput'),
    body: document.getElementById('chatBody'),
    quickWrap: document.getElementById('chatQuick')
  });

  /* ---- painel inline (contato.html) — mesma lógica, mesmos intents ---- */
  wireChatInterface({
    form: document.getElementById('chatFormInline'),
    input: document.getElementById('chatInputInline'),
    body: document.getElementById('chatBodyInline'),
    quickWrap: document.getElementById('chatQuickInline')
  });

  const openChatInline = document.getElementById('openChatInline');
  if (openChatInline && chatPanel) {
    openChatInline.addEventListener('click', () => chatPanel.classList.add('open'));
  }

  /* ---- Telegram deep link ---- */
  // Replace 'daiji_bot' with your real bot's @username once it exists
  // (create one via https://t.me/BotFather). Every "Comece pelo Telegram"
  // button uses this constant so you only change it in one place.
  const TELEGRAM_BOT_USERNAME = 'daiji_bot';
  document.querySelectorAll('[data-telegram-cta]').forEach(btn => {
    btn.href = `https://t.me/${TELEGRAM_BOT_USERNAME}`;
    btn.target = '_blank';
    btn.rel = 'noopener';
  });

  /* ---- booking modal (fully client-side simulation) ---- */
  const bookingModal = document.getElementById('bookingModal');
  if (bookingModal) {
    const bmDoctor = document.getElementById('bmDoctor');
    const bmSpecialty = document.getElementById('bmSpecialty');
    const bmSlots = document.getElementById('bmSlots');
    const bmForm = document.getElementById('bmForm');
    const bmStepPick = document.getElementById('bmStepPick');
    const bmStepConfirm = document.getElementById('bmStepConfirm');
    const bmProtocol = document.getElementById('bmProtocol');
    const bmSummary = document.getElementById('bmSummary');
    let selectedSlot = null;

    const openBooking = (name, specialty) => {
      bmDoctor.textContent = name;
      bmSpecialty.textContent = specialty;
      bmStepPick.style.display = '';
      bmStepConfirm.style.display = 'none';
      selectedSlot = null;
      bmSlots.querySelectorAll('.bm-slot').forEach(s => s.classList.remove('picked'));
      bookingModal.classList.add('open');
    };

    document.querySelectorAll('[data-book]').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        openBooking(btn.dataset.book, btn.dataset.specialty || '');
      });
    });

    document.querySelectorAll('#bookingModal .bm-close, #bookingModal .bm-overlay').forEach(el => {
      el.addEventListener('click', () => bookingModal.classList.remove('open'));
    });

    if (bmSlots) {
      bmSlots.querySelectorAll('.bm-slot').forEach(slot => {
        slot.addEventListener('click', () => {
          bmSlots.querySelectorAll('.bm-slot').forEach(s => s.classList.remove('picked'));
          slot.classList.add('picked');
          selectedSlot = slot.textContent.trim();
        });
      });
    }

    if (bmForm) {
      bmForm.addEventListener('submit', (e) => {
        e.preventDefault();
        if (!selectedSlot) { alert('Escolha um horário disponível.'); return; }
        const protocol = 'DAIJI-' + Math.random().toString(36).slice(2, 8).toUpperCase();

        // Persist locally so a returning visit still shows the booking —
        // this is a front-end simulation; swap for a real API call to your
        // scheduling backend when one exists.
        const bookings = JSON.parse(localStorage.getItem('daiji-bookings') || '[]');
        bookings.push({ protocol, doctor: bmDoctor.textContent, specialty: bmSpecialty.textContent, slot: selectedSlot });
        localStorage.setItem('daiji-bookings', JSON.stringify(bookings));

        bmProtocol.textContent = protocol;
        bmSummary.textContent = `${bmDoctor.textContent} · ${selectedSlot}`;
        bmStepPick.style.display = 'none';
        bmStepConfirm.style.display = '';
      });
    }
  }

  /* ---- score gauge fill (proportional to the example number shown) ---- */
  const scoreFill = document.getElementById('scoreFill');
  const scoreNum = document.getElementById('scoreNum');
  if (scoreFill && scoreNum) {
    const value = parseInt(scoreNum.textContent, 10) || 0;
    const circumference = 2 * Math.PI * 54; // r=54
    const offset = circumference - (value / 100) * circumference;
    requestAnimationFrame(() => { scoreFill.style.strokeDashoffset = offset; });
  }

});
