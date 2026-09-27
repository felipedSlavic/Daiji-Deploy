(function () {

    /* =====================================================
       VOCÊ
       (aparece em todos os círculos; o restante das pessoas
       é diferente em cada um)
    ====================================================== */

    /* usuário logado (salvo no login/cadastro) */
    function usuarioLogado() {
        try {
            const u = JSON.parse(localStorage.getItem('daiji_usuario') || 'null')
            if (u && u.nome) return u.nome.trim()
        } catch (e) { /* sem localStorage */ }
        return 'Você'
    }

    function iniciaisDe(nome) {
        const partes = nome.split(/\s+/).filter(Boolean)
        const letras = partes.length > 1
            ? partes[0][0] + partes[partes.length - 1][0]
            : partes[0].slice(0, 2)
        return letras.toUpperCase()
    }

    const NOME_USUARIO = usuarioLogado()

    const VOCE = {
        initials: iniciaisDe(NOME_USUARIO),
        avatar: 'avatar-green',
        name: NOME_USUARIO,
        you: true,
        // últimos 7 dias (o último item é hoje)
        progress: [1, 1, 1, 1, 1, 1, 1],
        streak: 9
    }


    /* =====================================================
       DADOS DOS CÍRCULOS
       progress: últimos 7 dias, da esquerda (6 dias atrás)
       para a direita (hoje). streak: dias seguidos até hoje
       (pode passar de 7, porque a sequência vem de antes).
    ====================================================== */

    const circleData = {

        familia: [
            { initials: 'AM', avatar: 'avatar-pink',   name: 'Ana Martins',     relacao: 'Mãe',     progress: [1, 1, 1, 1, 1, 1, 1], streak: 14 },
            { initials: 'CS', avatar: 'avatar-blue',   name: 'Carlos Santos',   relacao: 'Pai',     progress: [1, 0, 1, 1, 1, 1, 1], streak: 5 },
            { initials: 'MR', avatar: 'avatar-amber',  name: 'Marina Rocha',    relacao: 'Irmã',    progress: [0, 1, 1, 0, 1, 1, 0], streak: 0, quase: true },
            { initials: 'VS', avatar: 'avatar-purple', name: 'Vó Sônia',        relacao: 'Avó',     progress: [1, 1, 0, 1, 1, 1, 1], streak: 4 }
        ],

        amigos: [
            { initials: 'BF', avatar: 'avatar-teal',   name: 'Bianca Ferreira', progress: [1, 1, 1, 1, 1, 1, 1], streak: 21 },
            { initials: 'RA', avatar: 'avatar-blue',   name: 'Rafael Almeida',  progress: [1, 1, 1, 0, 1, 1, 1], streak: 3 },
            { initials: 'LP', avatar: 'avatar-coral',  name: 'Larissa Prado',   progress: [0, 1, 1, 1, 1, 1, 1], streak: 6 },
            { initials: 'TC', avatar: 'avatar-amber',  name: 'Thiago Cardoso',  progress: [1, 0, 0, 1, 0, 1, 1], streak: 2 },
            { initials: 'YM', avatar: 'avatar-pink',   name: 'Yasmin Moura',    progress: [0, 0, 1, 0, 1, 1, 0], streak: 0, quase: true },
            { initials: 'GH', avatar: 'avatar-purple', name: 'Gustavo Hideki',  progress: [0, 0, 0, 0, 0, 0, 1], streak: 1 }
        ],

        trabalho: [
            { initials: 'PN', avatar: 'avatar-coral',  name: 'Patrícia Nogueira', progress: [1, 1, 1, 1, 1, 1, 1], streak: 11 },
            { initials: 'DV', avatar: 'avatar-teal',   name: 'Diego Vieira',      progress: [1, 1, 0, 1, 1, 0, 1], streak: 1 },
            { initials: 'FO', avatar: 'avatar-blue',   name: 'Fábio Otsuka',      progress: [0, 1, 1, 1, 1, 1, 0], streak: 0, quase: true }
        ]

    }


    const TITULOS = {
        familia: 'Streaks da família',
        amigos: 'Streaks dos amigos',
        trabalho: 'Streaks do trabalho'
    }


    /* =====================================================
       ELEMENTOS
    ====================================================== */

    const circles = document.getElementById('circles')
    const streakTitle = document.getElementById('streakTitle')
    const membersList = document.getElementById('membersList')
    const rankingCard = document.getElementById('rankingCard')
    const highlightTitle = document.getElementById('highlightTitle')
    const supportButton = document.querySelector('.support-button')
    const inviteButton = document.getElementById('inviteButton')
    const toast = document.getElementById('toast')
    const toastMessage = document.getElementById('toastMessage')

    let toastTimer
    let circuloAtual = 'familia'


    /* =====================================================
       AUXILIARES
    ====================================================== */

    function pessoasDo(circle) {
        return [VOCE].concat(circleData[circle] || circleData.familia)
    }

    function diasAtivos(person) {
        return person.progress.reduce((a, b) => a + b, 0)
    }

    function primeiroNome(nome) {
        return nome.split(' ')[0]
    }

    function descricao(person) {

        if (person.streak === 0) {
            return {
                icon: 'bi-moon-stars paused',
                valor: 'Sem sequência hoje',
                label: diasAtivos(person) + ' de 7 dias ativos'
            }
        }

        if (person.streak >= 7) {
            return {
                icon: 'bi-fire fire',
                valor: person.streak + ' dias',
                label: 'seguidos · em chamas'
            }
        }

        if (person.streak >= 3) {
            return {
                icon: 'bi-fire fire',
                valor: person.streak + ' dias',
                label: 'de consistência'
            }
        }

        return {
            icon: 'bi-flower1 sprout',
            valor: person.streak + (person.streak === 1 ? ' dia' : ' dias'),
            label: 'começando uma sequência'
        }

    }


    /* =====================================================
       MEMBROS
    ====================================================== */

    function renderMembers(circle) {

        if (!membersList) return

        const people = pessoasDo(circle)
            .slice()
            .sort((a, b) => b.streak - a.streak || diasAtivos(b) - diasAtivos(a))

        membersList.innerHTML = people.map(person => {

            const d = descricao(person)

            const progressHtml = person.progress
                .map((on, i) =>
                    `<i class="${on ? 'on' : ''} ${i === 6 ? 'today' : ''}"></i>`
                )
                .join('')

            const actions = person.you
                ? `<span class="streak-badge">
                       <i class="bi bi-fire"></i>
                       ${person.streak}
                   </span>`
                : `<button
                       class="message-button"
                       type="button"
                       data-name="${primeiroNome(person.name)}"
                       aria-label="Enviar mensagem para ${person.name}"
                   >
                       <i class="bi bi-chat"></i>
                   </button>`

            return `
                <article class="member-card ${person.you ? 'me' : ''}">

                    <div class="avatar ${person.avatar}">
                        ${person.initials}
                    </div>

                    <div class="member-info">

                        <div class="member-name">
                            <strong>${person.name}</strong>
                            ${person.you ? '<span class="you-tag">você</span>' : ''}
                            ${person.relacao ? `<span class="relation-tag">${person.relacao}</span>` : ''}
                        </div>

                        <div class="member-streak">
                            <i class="bi ${d.icon}"></i>
                            <strong>${d.valor}</strong>
                            <span>${d.label}</span>
                        </div>

                        <div class="streak-progress" aria-label="${diasAtivos(person)} de 7 dias ativos">
                            ${progressHtml}
                        </div>

                    </div>

                    <div class="member-actions">
                        ${actions}
                    </div>

                </article>
            `

        }).join('')

    }


    /* =====================================================
       RANKING "MAIS CONSISTENTES" (por círculo)
    ====================================================== */

    function renderRanking(circle) {

        if (!rankingCard) return

        const people = pessoasDo(circle)
            .slice()
            .sort((a, b) => diasAtivos(b) - diasAtivos(a) || b.streak - a.streak)
            .slice(0, 4)

        rankingCard.innerHTML = people.map((person, i) => `
            <div class="ranking-row ${i === 0 ? 'first' : ''} ${person.you ? 'me-row' : ''}">

                <span class="position">${i + 1}</span>

                <div class="mini-avatar ${person.avatar}">
                    ${person.initials}
                </div>

                <span class="ranking-name">
                    ${person.name}
                    ${person.you ? '<small>você</small>' : ''}
                </span>

                <span class="ranking-value" title="dias ativos na semana">
                    <i class="bi ${person.streak >= 3 ? 'bi-fire' : 'bi-flower1'}"></i>
                    ${diasAtivos(person)}/7
                </span>

            </div>
        `).join('')

    }


    /* =====================================================
       "ALGUÉM PRECISA DE VOCÊ" (por círculo)
    ====================================================== */

    function renderHighlight(circle) {

        const alvo =
            (circleData[circle] || []).find(p => p.quase) ||
            (circleData[circle] || []).slice().sort((a, b) => a.streak - b.streak)[0]

        if (!alvo) return

        if (highlightTitle) {
            highlightTitle.textContent =
                alvo.streak === 0
                    ? primeiroNome(alvo.name) + ' perdeu a sequência hoje — um incentivo ajuda a voltar'
                    : primeiroNome(alvo.name) + ' está perto de completar uma nova sequência'
        }

        if (supportButton) {
            supportButton.disabled = false
            supportButton.dataset.name = primeiroNome(alvo.name)
            supportButton.innerHTML = '<i class="bi bi-heart"></i> Incentivar ' + primeiroNome(alvo.name)
        }

    }


    /* =====================================================
       ATUALIZA TUDO
    ====================================================== */

    function render(circle) {

        circuloAtual = circle

        if (streakTitle) {
            streakTitle.textContent = TITULOS[circle] || 'Streaks'
        }

        renderMembers(circle)
        renderRanking(circle)
        renderHighlight(circle)

        requestAnimationFrame(animateStreaks)

    }


    /* contadores dos botões de círculo */
    if (circles) {

        circles.querySelectorAll('.circle').forEach(button => {
            const count = button.querySelector('.circle-count')
            if (count) count.textContent = pessoasDo(button.dataset.circle).length
        })

        circles.addEventListener('click', event => {

            const button = event.target.closest('.circle')

            if (!button) return

            circles.querySelectorAll('.circle').forEach(item => {
                item.setAttribute('aria-pressed', 'false')
                item.classList.remove('active')
            })

            button.setAttribute('aria-pressed', 'true')
            button.classList.add('active')

            render(button.dataset.circle)

        })

    }


    /* =====================================================
       ANIMAÇÃO DAS BARRAS
    ====================================================== */

    function animateStreaks() {

        document
            .querySelectorAll('.streak-progress i.on')
            .forEach((bar, index) => {

                bar.style.transform = 'scaleX(0)'
                bar.style.transformOrigin = 'left'
                bar.style.transition = 'transform 0.4s cubic-bezier(.2,.8,.2,1)'

                setTimeout(() => {
                    bar.style.transform = 'scaleX(1)'
                }, 80 + index * 22)

            })

    }


    /* =====================================================
       AÇÕES
    ====================================================== */

    if (inviteButton) {
        inviteButton.addEventListener('click', () => {
            showToast('Convite da comunidade pronto para compartilhar')
        })
    }


    // mensagens (delegação, pois a lista é recriada a cada círculo)
    if (membersList) {
        membersList.addEventListener('click', event => {
            const button = event.target.closest('.message-button')
            if (!button) return
            showToast('Abrindo conversa com ' + button.dataset.name)
        })
    }


    if (supportButton) {
        supportButton.addEventListener('click', () => {
            const nome = supportButton.dataset.name || ''
            supportButton.innerHTML = '<i class="bi bi-heart-fill"></i> Incentivo enviado'
            supportButton.disabled = true
            showToast('Seu incentivo foi enviado para ' + nome)
        })
    }


    /* =====================================================
       TOAST
    ====================================================== */

    function showToast(message) {

        if (!toast || !toastMessage) return

        toastMessage.textContent = message
        toast.classList.add('show')

        clearTimeout(toastTimer)

        toastTimer = setTimeout(() => {
            toast.classList.remove('show')
        }, 2400)

    }


    /* =====================================================
       INICIALIZAÇÃO
    ====================================================== */

    render(circuloAtual)

})()
