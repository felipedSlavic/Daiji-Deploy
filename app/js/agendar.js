(function () {

    /* =====================================================
       MÉDICOS (dados em js/medicos.js)
    ====================================================== */

    const MEDICOS = window.DAIJI_MEDICOS || []
    const ESPECIALIDADES = window.DAIJI_ESPECIALIDADES || {}

    const pickerEl = document.getElementById('picker')
    const pickerTabs = document.getElementById('pickerTabs')
    const pickerClose = document.getElementById('pickerClose')
    const paneEspecialidade = document.getElementById('paneEspecialidade')
    const paneMedico = document.getElementById('paneMedico')
    const specGrid = document.getElementById('specGrid')
    const specDoctorsWrap = document.getElementById('specDoctorsWrap')
    const specDoctors = document.getElementById('specDoctors')
    const allDoctors = document.getElementById('allDoctors')
    const doctorSearch = document.getElementById('doctorSearch')
    const doctorCard = document.getElementById('doctorCard')
    const changeDoctor = document.getElementById('changeDoctor')
    const scheduleColumn = document.getElementById('scheduleColumn')
    const ctaLabel = document.getElementById('ctaLabel')

    let especialidadeEscolhida = null


    function normalizar(texto) {
        return String(texto)
            .toLowerCase()
            .normalize('NFD')
            .replace(/[̀-ͯ]/g, '')
            .trim()
    }


    function itemMedico(m, mostrarEspecialidade) {

        const esp = ESPECIALIDADES[m.especialidade] || {}
        const selecionado = state.medico && state.medico.id === m.id

        return `
            <button
                type="button"
                class="pick-doctor"
                data-id="${m.id}"
                aria-pressed="${selecionado ? 'true' : 'false'}"
            >
                <span class="pick-avatar">${window.DAIJI_iniciais(m.nome)}</span>

                <span class="pick-info">
                    <strong>${m.nome}</strong>
                    <small>
                        ${mostrarEspecialidade ? (esp.nome + ' · ') : ''}${m.titulo} · ${m.registro}
                    </small>
                    <span class="pick-meta">
                        <span><i class="bi bi-star-fill"></i> ${m.nota.toFixed(1)}</span>
                        <span>${m.avaliacoes} avaliações</span>
                        <span><i class="bi bi-clock"></i> ${m.duracao} min</span>
                    </span>
                </span>

                <i class="bi bi-chevron-right pick-arrow"></i>
            </button>
        `
    }


    function renderEspecialidades() {

        if (!specGrid) return

        specGrid.innerHTML = Object.keys(ESPECIALIDADES)
            .map(key => {

                const esp = ESPECIALIDADES[key]
                const total = MEDICOS.filter(m => m.especialidade === key).length

                return `
                    <button
                        type="button"
                        class="spec-chip"
                        data-spec="${key}"
                        aria-pressed="${especialidadeEscolhida === key ? 'true' : 'false'}"
                    >
                        <i class="bi ${esp.icone}"></i>
                        <span>${esp.nome}</span>
                        <small>${total} médicos</small>
                    </button>
                `

            })
            .join('')

    }


    function renderMedicosDaEspecialidade() {

        if (!specDoctors || !specDoctorsWrap) return

        if (!especialidadeEscolhida) {
            specDoctorsWrap.hidden = true
            return
        }

        const lista = MEDICOS
            .filter(m => m.especialidade === especialidadeEscolhida)
            .sort((a, b) => b.nota - a.nota || b.avaliacoes - a.avaliacoes)

        specDoctors.innerHTML = lista.map(m => itemMedico(m, false)).join('')
        specDoctorsWrap.hidden = false

    }


    function renderTodosMedicos() {

        if (!allDoctors) return

        const termo = normalizar(doctorSearch ? doctorSearch.value : '')

        const lista = MEDICOS
            .filter(m => {
                const esp = ESPECIALIDADES[m.especialidade] || {}
                return !termo || normalizar(
                    [m.nome, m.titulo, m.registro, esp.nome, esp.categoria].join(' ')
                ).includes(termo)
            })
            .sort((a, b) => a.nome.replace(/^Dr[a]?\.\s*/, '').localeCompare(b.nome.replace(/^Dr[a]?\.\s*/, ''), 'pt-BR'))

        allDoctors.innerHTML = lista.length
            ? lista.map(m => itemMedico(m, true)).join('')
            : '<p class="picker-empty">Nenhum médico encontrado</p>'

    }


    function trocarAba(aba) {

        if (!pickerTabs) return

        pickerTabs.querySelectorAll('.picker-tab').forEach(tab => {
            tab.setAttribute('aria-selected', tab.dataset.tab === aba ? 'true' : 'false')
        })

        if (paneEspecialidade) paneEspecialidade.hidden = aba !== 'especialidade'
        if (paneMedico) paneMedico.hidden = aba !== 'medico'

        if (aba === 'medico') renderTodosMedicos()

    }


    function abrirSeletor() {

        if (pickerEl) pickerEl.hidden = false
        if (doctorCard) doctorCard.hidden = true
        if (pickerClose) pickerClose.hidden = !state.medico

        if (state.medico) {
            especialidadeEscolhida = state.medico.especialidade
        }

        renderEspecialidades()
        renderMedicosDaEspecialidade()
        renderTodosMedicos()

        if (pickerEl) pickerEl.scrollIntoView({ behavior: 'smooth', block: 'start' })

    }


    function fecharSeletor() {

        if (!state.medico) return

        if (pickerEl) pickerEl.hidden = true
        if (doctorCard) doctorCard.hidden = false

    }


    function escolherMedico(medico) {

        state.medico = medico

        const esp = ESPECIALIDADES[medico.especialidade] || {}
        const duracao = medico.duracao + ' min'

        const campos = {
            docEyebrow: esp.nome,
            docNome: medico.nome,
            docEspecialidade: medico.titulo + ' · ' + medico.registro,
            docNota: medico.nota.toFixed(1),
            docAvaliacoes: medico.avaliacoes + ' avaliações',
            docDuracao: duracao,
            docAvatar: window.DAIJI_iniciais(medico.nome),
            recapProfissional: medico.nome,
            sumProfissional: medico.nome,
            sumDuracao: medico.duracao + ' minutos'
        }

        Object.keys(campos).forEach(id => {
            const el = document.getElementById(id)
            if (el) el.textContent = campos[id]
        })

        document.querySelectorAll('.js-duracao').forEach(el => {
            el.textContent = duracao
        })

        if (doctorCard) doctorCard.classList.add('has-initials')

        // guarda na URL para voltar/atualizar sem perder a escolha
        try {
            const url = new URL(window.location.href)
            url.searchParams.delete('especialidade')
            url.searchParams.set('medico', medico.id)
            history.replaceState(null, '', url)
        } catch (e) { /* sem suporte: segue normal */ }

        fecharSeletor()

        if (typeof sync === 'function' && datesReady) sync()

    }


    /* eventos do seletor */

    if (pickerTabs) {
        pickerTabs.addEventListener('click', event => {
            const tab = event.target.closest('.picker-tab')
            if (tab) trocarAba(tab.dataset.tab)
        })
    }

    if (specGrid) {
        specGrid.addEventListener('click', event => {
            const chip = event.target.closest('.spec-chip')
            if (!chip) return
            especialidadeEscolhida = chip.dataset.spec
            renderEspecialidades()
            renderMedicosDaEspecialidade()
            if (specDoctorsWrap) specDoctorsWrap.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
        })
    }

    ;[specDoctors, allDoctors].forEach(lista => {
        if (!lista) return
        lista.addEventListener('click', event => {
            const btn = event.target.closest('.pick-doctor')
            if (!btn) return
            const medico = window.DAIJI_medicoPorId(btn.dataset.id)
            if (medico) escolherMedico(medico)
        })
    })

    if (doctorSearch) {
        doctorSearch.addEventListener('input', renderTodosMedicos)
    }

    if (changeDoctor) {
        changeDoctor.addEventListener('click', abrirSeletor)
    }

    if (pickerClose) {
        pickerClose.addEventListener('click', fecharSeletor)
    }


    function iniciarSeletor() {

        const params = new URLSearchParams(window.location.search)
        const medicoParam = window.DAIJI_medicoPorId(params.get('medico'))
        const espParam = params.get('especialidade')

        if (medicoParam) {
            escolherMedico(medicoParam)
            return
        }

        if (espParam && ESPECIALIDADES[espParam]) {
            especialidadeEscolhida = espParam
        }

        abrirSeletor()
        trocarAba('especialidade')

        // sem profissional escolhido ainda
        if (scheduleColumn) scheduleColumn.classList.add('is-locked')

    }


    /* =====================================================
       CONFIGURAÇÕES
    ====================================================== */

    const WEEKDAYS = [
        'dom',
        'seg',
        'ter',
        'qua',
        'qui',
        'sex',
        'sáb'
    ]


    const MONTHS = [
        'jan',
        'fev',
        'mar',
        'abr',
        'mai',
        'jun',
        'jul',
        'ago',
        'set',
        'out',
        'nov',
        'dez'
    ]


    const MONTHS_FULL = [
        'Janeiro',
        'Fevereiro',
        'Março',
        'Abril',
        'Maio',
        'Junho',
        'Julho',
        'Agosto',
        'Setembro',
        'Outubro',
        'Novembro',
        'Dezembro'
    ]


    /* =====================================================
       HORÁRIOS DISPONÍVEIS
    ====================================================== */

    const TEMPLATE = {

        manha: [
            ['08:00', true],
            ['08:50', false],
            ['09:40', true],
            ['10:30', true],
            ['11:20', false]
        ],

        tarde: [
            ['13:00', true],
            ['13:50', true],
            ['14:40', false],
            ['15:30', true],
            ['16:20', true],
            ['17:10', true]
        ],

        noite: [
            ['18:00', true],
            ['18:50', false],
            ['19:40', true],
            ['20:30', true]
        ]

    }


    /* =====================================================
       ESTADO
    ====================================================== */

    const state = {

        day: null,

        period: 'manha',

        time: null,

        type: 'Vídeo',

        medico: null

    }

    // evita chamar sync() antes de montar a agenda
    let datesReady = false


    /* =====================================================
       ELEMENTOS
    ====================================================== */

    const datesEl =
        document.getElementById('dates')

    const slotsEl =
        document.getElementById('slots')

    const cta =
        document.getElementById('cta')

    const monthLbl =
        document.getElementById('monthLbl')

    const tzLbl =
        document.getElementById('tzLbl')

    const sumWhen =
        document.getElementById('sumWhen')

    const sumType =
        document.getElementById('sumType')

    const modal =
        document.getElementById('modal')

    const recapWhen =
        document.getElementById('recapWhen')

    const recapType =
        document.getElementById('recapType')


    /* =====================================================
       VERIFICAÇÃO
    ====================================================== */

    if (!datesEl || !slotsEl || !cta) {

        console.error(
            'DAIJI: elementos da agenda não foram encontrados no HTML'
        )

        return

    }


    /* =====================================================
       PRÓXIMOS 14 DIAS
    ====================================================== */

    const days = []

    const currentDate = new Date()

    currentDate.setHours(
        0,
        0,
        0,
        0
    )


    while (days.length < 14) {

        const dayOfWeek =
            currentDate.getDay()

        days.push({

            date:
                new Date(currentDate),

            open:
                dayOfWeek !== 0

        })


        currentDate.setDate(
            currentDate.getDate() + 1
        )

    }


    /* =====================================================
       CONSTRÓI AS DATAS
    ====================================================== */

    let firstOpen = null


    days.forEach((item, index) => {

        const button =
            document.createElement('button')


        button.type =
            'button'

        button.className =
            'day'

        button.setAttribute(
            'aria-pressed',
            'false'
        )


        if (!item.open) {

            button.disabled =
                true

        }


        button.innerHTML = `

            <div class="dow">

                ${WEEKDAYS[item.date.getDay()]}

            </div>

            <div class="dnum">

                ${item.date.getDate()}

            </div>

            <div class="dmon">

                ${MONTHS[item.date.getMonth()]}

            </div>

            <div class="dot"></div>

        `


        item.el =
            button


        if (item.open) {

            button.addEventListener(
                'click',
                () => {

                    selectDay(
                        index,
                        button
                    )

                }
            )


            if (firstOpen === null) {

                firstOpen = {

                    index,
                    element: button

                }

            }

        }


        datesEl.appendChild(
            button
        )

    })


    /* =====================================================
       FORMATA DATA E HORÁRIO
    ====================================================== */

    function formatWhen(longFormat = false) {

        if (
            state.day === null ||
            !state.time
        ) {

            return '—'

        }


        const selectedDate =
            days[state.day].date


        const weekday =
            WEEKDAYS[
                selectedDate.getDay()
            ]


        if (longFormat) {

            return (
                weekday +
                ', ' +
                selectedDate.getDate() +
                ' de ' +
                MONTHS_FULL[
                    selectedDate.getMonth()
                ].toLowerCase() +
                ' · ' +
                state.time
            )

        }


        const month =
            String(
                selectedDate.getMonth() + 1
            ).padStart(
                2,
                '0'
            )


        return (
            weekday +
            ' ' +
            selectedDate.getDate() +
            '/' +
            month +
            ' · ' +
            state.time
        )

    }


    /* =====================================================
       CONTADORES MANHÃ / TARDE / NOITE
    ====================================================== */

    function updateCounts() {

        Object.keys(
            TEMPLATE
        ).forEach(period => {

            const total =
                TEMPLATE[period]
                    .filter(
                        item => item[1]
                    )
                    .length


            const counter =
                document.querySelector(
                    `[data-count="${period}"]`
                )


            if (counter) {

                counter.textContent =
                    `(${total})`

            }

        })

    }


    /* =====================================================
       MONTA OS HORÁRIOS
    ====================================================== */

    function renderSlots() {

        slotsEl.innerHTML =
            ''


        if (state.day === null) {

            slotsEl.innerHTML = `

                <div class="slots-empty">

                    Selecione um dia para ver os horários

                </div>

            `

            return

        }


        const list =
            TEMPLATE[state.period]


        list.forEach(item => {

            const time =
                item[0]

            const available =
                item[1]


            const button =
                document.createElement('button')


            button.type =
                'button'

            button.className =
                'slot'

            button.textContent =
                time


            button.setAttribute(

                'aria-pressed',

                state.time === time
                    ? 'true'
                    : 'false'

            )


            if (!available) {

                button.disabled =
                    true

                button.title =
                    'Horário indisponível'

            }

            else {

                button.addEventListener(
                    'click',
                    () => {

                        state.time =
                            time

                        renderSlots()

                        sync()

                    }
                )

            }


            slotsEl.appendChild(
                button
            )

        })

    }


    /* =====================================================
       SELECIONA O DIA
    ====================================================== */

    function selectDay(
        index,
        element
    ) {

        state.day =
            index

        state.time =
            null


        days.forEach(item => {

            item.el.setAttribute(
                'aria-pressed',
                'false'
            )

        })


        element.setAttribute(
            'aria-pressed',
            'true'
        )


        if (monthLbl) {

            monthLbl.textContent =
                MONTHS_FULL[
                    days[index]
                        .date
                        .getMonth()
                ]

        }


        renderSlots()

        sync()

    }


    /* =====================================================
       SINCRONIZA RESUMO
    ====================================================== */

    function sync() {

        if (sumWhen) {

            sumWhen.textContent =
                formatWhen()

        }


        if (sumType) {

            sumType.textContent =

                state.type === 'Vídeo'
                    ? 'Por vídeo'
                    : 'Presencial'

        }


        const ready =

            state.medico !== null &&
            state.day !== null &&
            state.time !== null


        cta.disabled =
            !ready


        if (scheduleColumn) {
            scheduleColumn.classList.toggle('is-locked', !state.medico)
        }


        if (ctaLabel) {

            ctaLabel.textContent =
                !state.medico
                    ? 'Escolha um profissional'
                    : !state.time
                        ? 'Escolha um horário'
                        : 'Confirmar agendamento'

        }

    }


    /* =====================================================
       TIPO DE CONSULTA
    ====================================================== */

    const types =
        document.getElementById('types')


    if (types) {

        types.addEventListener(
            'click',
            event => {

                const button =
                    event.target.closest(
                        '.type'
                    )


                if (!button) {

                    return

                }


                document
                    .querySelectorAll(
                        '.type'
                    )
                    .forEach(item => {

                        item.setAttribute(
                            'aria-pressed',
                            'false'
                        )

                    })


                button.setAttribute(
                    'aria-pressed',
                    'true'
                )


                state.type =
                    button.dataset.type


                if (tzLbl) {

                    tzLbl.textContent =

                        state.type === 'Vídeo'

                            ? 'Horário de Brasília'

                            : 'Consultório · Jardins, SP'

                }


                sync()

            }
        )

    }


    /* =====================================================
       PERÍODO
    ====================================================== */

    const periods =
        document.getElementById('periods')


    if (periods) {

        periods.addEventListener(
            'click',
            event => {

                const button =
                    event.target.closest(
                        '.period'
                    )


                if (!button) {

                    return

                }


                document
                    .querySelectorAll(
                        '.period'
                    )
                    .forEach(item => {

                        item.setAttribute(
                            'aria-pressed',
                            'false'
                        )

                    })


                button.setAttribute(
                    'aria-pressed',
                    'true'
                )


                state.period =
                    button.dataset.period


                state.time =
                    null


                renderSlots()

                sync()

            }
        )

    }


    /* =====================================================
       CONFIRMAR AGENDAMENTO
    ====================================================== */

    cta.addEventListener(
        'click',
        () => {

            if (cta.disabled) {

                return

            }


            if (recapWhen) {

                recapWhen.textContent =
                    formatWhen(true)

            }


            if (recapType) {

                recapType.textContent =

                    state.type === 'Vídeo'

                        ? 'Por vídeo (online)'

                        : 'Presencial'

            }


            if (modal) {

                modal.hidden =
                    false

            }

        }
    )


    /* =====================================================
       FECHAR MODAL
    ====================================================== */

    const closeButton =
        document.getElementById('close')

    const doneButton =
        document.getElementById('done')


    if (closeButton) {

        closeButton.addEventListener(
            'click',
            () => {

                modal.hidden =
                    true

            }
        )

    }


    if (doneButton) {

        doneButton.addEventListener(
            'click',
            () => {

                modal.hidden =
                    true

            }
        )

    }


    if (modal) {

        modal.addEventListener(
            'click',
            event => {

                if (
                    event.target === modal
                ) {

                    modal.hidden =
                        true

                }

            }
        )

    }


    /* =====================================================
       FAVORITAR
    ====================================================== */

    const favoriteButton =
        document.querySelector(
            '.favorite-top'
        )


    if (favoriteButton) {

        favoriteButton.addEventListener(
            'click',
            () => {

                const icon =
                    favoriteButton.querySelector(
                        'i'
                    )


                const isFavorite =
                    icon.classList.contains(
                        'bi-heart-fill'
                    )


                icon.classList.toggle(
                    'bi-heart',
                    isFavorite
                )

                icon.classList.toggle(
                    'bi-heart-fill',
                    !isFavorite
                )


                favoriteButton.classList.toggle(
                    'selected',
                    !isFavorite
                )

            }
        )

    }


    /* =====================================================
       INICIALIZAÇÃO
    ====================================================== */

    datesReady = true

    iniciarSeletor()


    updateCounts()


    if (firstOpen) {

        selectDay(
            firstOpen.index,
            firstOpen.element
        )

    }

    else {

        renderSlots()

    }


    sync()

})()