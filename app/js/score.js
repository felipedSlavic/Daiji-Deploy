(function () {
    if (!DaijiSession.validar()) return

    /* =====================================================
       CONFIGURAÇÕES
    ====================================================== */

    const STORE_KEY =
        'daiji_checkin'


    let points =
        1250

    let checkinEmAndamento = false
    let contextoBeneficiarioInvalidado = false
    const idBeneficiarioPagina = DaijiSession.obterIdBeneficiario()
    let temDiabetes = false
    try {
        temDiabetes = JSON.parse(localStorage.getItem('daiji_personalizacao:' + idBeneficiarioPagina) || 'null')?.diabetes === 'sim'
    } catch { /* Preferência visual ausente. */ }

    const answers = {

        sono: null,

        // duração do sono em horas (ex.: 7.5 = 7h30)
        horasSono: 7.5,

        // como o usuário informou: 'horas' ou 'intervalo'
        sonoModo: 'horas',

        dormiu: '23:00',

        acordou: '06:30',

        estresse: null,

        dieta: null,

        remedio: null,

        passos: null,

        agua: null,

        paSis: null,

        paDia: null,

        glicemia: null,

        medicaoGlicemia: null

    }


    const required = [

        'sono',

        'estresse',

        'dieta',

        'remedio',

        'passos',

        'agua'

    ]


    if (temDiabetes) {

        required.push(
            'glicemia'
        )

        required.push(
            'medicaoGlicemia'
        )

    }


    /* =====================================================
       ELEMENTOS
    ====================================================== */

    const modal =
        document.getElementById('modal')

    const flow =
        document.getElementById('ciFlow')

    const success =
        document.getElementById('ciSuccess')

    const submit =
        document.getElementById('ciSubmit')

    const progress =
        document.getElementById('ciProg')

    const ptsNum =
        document.getElementById('ptsNum')

    const actionCard =
        document.getElementById('actionCard')

    const actionTitle =
        document.getElementById('actionTitle')

    const actionSub =
        document.getElementById('actionSub')

    const actionIc =
        document.getElementById('actionIc')

    const today =
        document.getElementById('wtoday')

    const toast =
        document.getElementById('toast')

    const toastMsg =
        document.getElementById('toastMsg')


    /* =====================================================
       DATA ATUAL
    ====================================================== */

    function updateDate() {

        const element =
            document.getElementById('todayDate')


        if (!element) {

            return

        }


        const now =
            new Date()


        element.textContent =
            now.toLocaleDateString(
                'pt-BR',
                {
                    weekday: 'long',
                    day: '2-digit',
                    month: 'long'
                }
            )

    }


    /* =====================================================
       SCORE
    ====================================================== */

    function animateScore(score) {

        const arc =
            document.getElementById(
                'scoreArc'
            )


        if (!arc) {

            return

        }


        const radius =
            60


        const circumference =
            2 *
            Math.PI *
            radius


        arc.style.strokeDasharray =
            circumference


        arc.style.strokeDashoffset =
            circumference

        arc.style.visibility = 'visible'


        requestAnimationFrame(
            () => {

                setTimeout(
                    () => {

                        arc.style.strokeDashoffset =
                            circumference *
                            (
                                1 -
                                score / 100
                            )

                    },
                    150
                )

            }
        )

    }


    async function carregarScore(preservarAtual = false, beneficiarioEsperado = null) {
        const valor = document.getElementById('scoreValue')
        const classificacao = document.querySelector('.score-content h2')
        const atualizacao = document.getElementById('scoreUpdate')
        const arc = document.getElementById('scoreArc')

        function mostrarEstado(titulo, mensagem) {
            if (preservarAtual) return
            valor.textContent = '—'
            arc.style.visibility = 'hidden'
            classificacao.textContent = titulo
            atualizacao.textContent = mensagem
        }

        mostrarEstado('Carregando score...', 'Consultando seu score.')

        const idBeneficiario = DaijiSession.obterIdBeneficiario()
        if (idBeneficiario === null) {
            mostrarEstado('Identificação necessária', 'Faça login com uma conta de beneficiário para consultar seu score.')
            return false
        }

        if (beneficiarioEsperado !== null && idBeneficiario !== beneficiarioEsperado) return false
        if (!validarContextoBeneficiario()) return false

        let resposta
        try {
            resposta = await DaijiHttp.request(`/api/beneficiarios/${idBeneficiario}/score`)
        } catch (erro) {
            mostrarEstado('Score indisponível', DaijiHttp.isTimeout(erro)
                ? 'O servidor demorou para responder. Recarregue a página para tentar novamente.'
                : 'Não foi possível conectar ao serviço. Tente novamente mais tarde.')
            return false
        }

        if (resposta.status === 404) {
            mostrarEstado('Score ainda não calculado', 'Seu score estará disponível após o primeiro cálculo.')
            return false
        }
        if (resposta.status === 400) {
            mostrarEstado('Identificação inválida', 'Não foi possível identificar o beneficiário. Faça login novamente.')
            return false
        }
        if (resposta.status !== 200) {
            mostrarEstado('Score indisponível', 'Não foi possível consultar seu score. Tente novamente mais tarde.')
            return false
        }

        try {
            const dados = await resposta.json()
            const riscos = { BAIXO: 'Risco Baixo', MEDIO: 'Risco Moderado', ALTO: 'Risco Alto' }
            const data = new Date(dados?.dataCalculo)
            if (!dados || dados.idBeneficiario !== idBeneficiario ||
                typeof dados.valorScore !== 'number' || !Number.isFinite(dados.valorScore) ||
                dados.valorScore < 0 || dados.valorScore > 100 ||
                !Object.prototype.hasOwnProperty.call(riscos, dados.classificacaoRisco) ||
                typeof dados.dataCalculo !== 'string' || Number.isNaN(data.getTime())) {
                throw new Error('Resposta de score inválida')
            }

            if (!validarContextoBeneficiario()) return false

            valor.textContent = dados.valorScore.toLocaleString('pt-BR')
            classificacao.textContent = riscos[dados.classificacaoRisco]
            atualizacao.textContent = 'Atualizado em ' + data.toLocaleString('pt-BR', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit'
            })
            animateScore(dados.valorScore)
            return true
        } catch {
            mostrarEstado('Score indisponível', 'Não foi possível carregar seu score. Tente novamente mais tarde.')
            return false
        }
    }


    /* =====================================================
       CÁLCULO DE PONTOS DO CHECK-IN
    ====================================================== */

    function calcPointsEarned() {

        let pts =
            50


        if (
            answers.paSis &&
            answers.paDia
        ) {

            pts +=
                10

        }


        if (
            temDiabetes &&
            answers.glicemia
        ) {

            pts +=
                10

        }


        return pts

    }


    /* =====================================================
       PONTOS
    ====================================================== */

    function formatPoints(value) {

        return value.toLocaleString(
            'pt-BR'
        )

    }


    function animatePoints(
        from,
        to
    ) {

        const start =
            performance.now()

        const duration =
            800


        function step(now) {

            const progress =
                Math.min(
                    1,
                    (
                        now -
                        start
                    ) /
                    duration
                )


            const value =
                Math.round(

                    from +

                    (
                        to -
                        from
                    ) *

                    (
                        1 -
                        Math.pow(
                            1 -
                            progress,
                            3
                        )
                    )

                )


            ptsNum.textContent =
                formatPoints(value)


            if (
                progress <
                1
            ) {

                requestAnimationFrame(
                    step
                )

            }

        }


        requestAnimationFrame(
            step
        )

    }


    /* =====================================================
       PROGRESSO DO CHECK-IN
    ====================================================== */

    function updateProgress() {

        const completed =
            required.filter(
                key =>
                    answers[key] !==
                        null &&
                    answers[key] !==
                        ''
            ).length


        const percentage =
            Math.round(
                completed /
                required.length *
                100
            )


        progress.style.width =
            percentage +
            '%'


        submit.disabled =
            contextoBeneficiarioInvalidado || checkinEmAndamento || actionCard.classList.contains('done') ||
            completed < required.length


        updatePtsDisplay()

    }


    function updatePtsDisplay() {

        const ganhos =
            calcPointsEarned()


        const kicker =
            document.getElementById(
                'ciPtsKicker'
            )

        const submitPts =
            document.getElementById(
                'ciSubmitPts'
            )


        if (kicker) {

            kicker.textContent =
                '+' +
                ganhos +
                ' pontos'

        }


        if (submitPts) {

            submitPts.textContent =
                '+' +
                ganhos +
                ' pts'

        }

    }


    /* =====================================================
       CHIPS
    ====================================================== */

    document
        .querySelectorAll(
            '.chips'
        )
        .forEach(group => {

            const key =
                group.dataset.group


            group.addEventListener(
                'click',
                event => {

                    const button =
                        event.target.closest(
                            '.chip'
                        )


                    if (!button) {

                        return

                    }


                    group
                        .querySelectorAll(
                            '.chip'
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


                    answers[key] =
                        button.dataset.val


                    updateProgress()

                }
            )

        })


    /* =====================================================
       OPÇÕES
    ====================================================== */

    document
        .querySelectorAll(
            '.options'
        )
        .forEach(group => {

            const key =
                group.dataset.group


            group.addEventListener(
                'click',
                event => {

                    const button =
                        event.target.closest(
                            '.option'
                        )


                    if (!button) {

                        return

                    }


                    group
                        .querySelectorAll(
                            '.option'
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


                    answers[key] =
                        button.dataset.val


                    updateProgress()

                }
            )

        })


    /* =====================================================
       HORÁRIO
    ====================================================== */

    /* =====================================================
       DURAÇÃO DO SONO
       (total de horas OU intervalo dormi → acordei)
    ====================================================== */

    const sleepModeEl =
        document.getElementById('sleepMode')

    const sleepPanelHoras =
        document.getElementById('sleepPanelHoras')

    const sleepPanelIntervalo =
        document.getElementById('sleepPanelIntervalo')

    const sleepHorasValor =
        document.getElementById('sleepHorasValor')

    const sleepMenos =
        document.getElementById('sleepMenos')

    const sleepMais =
        document.getElementById('sleepMais')

    const dormiuInput =
        document.getElementById('dormiuInput')

    const acordouInput =
        document.getElementById('acordouInput')

    const sleepResult =
        document.getElementById('sleepResult')

    const sleepResultText =
        document.getElementById('sleepResultText')


    const SONO_MIN = 0
    const SONO_MAX = 16
    const SONO_PASSO = 0.5

    // valor guardado no modo "total de horas"
    let horasManuais = answers.horasSono


    function formatarDuracao(horas) {

        const totalMin = Math.round(horas * 60)
        const h = Math.floor(totalMin / 60)
        const m = totalMin % 60

        if (m === 0) {
            return h + 'h'
        }

        return h + 'h ' + String(m).padStart(2, '0') + 'min'

    }


    // diferença entre dois horários, atravessando a meia-noite
    function calcularIntervalo(inicio, fim) {

        if (!inicio || !fim) {
            return null
        }

        const [h1, m1] = inicio.split(':').map(Number)
        const [h2, m2] = fim.split(':').map(Number)

        let minutos = (h2 * 60 + m2) - (h1 * 60 + m1)

        if (minutos <= 0) {
            minutos += 24 * 60
        }

        return minutos / 60

    }


    function classificarSono(horas) {

        if (horas < 5) return { txt: 'bem abaixo do recomendado', nivel: 'ruim' }
        if (horas < 7) return { txt: 'abaixo do recomendado (7–9h)', nivel: 'atencao' }
        if (horas <= 9) return { txt: 'dentro do recomendado', nivel: 'bom' }
        return { txt: 'acima do recomendado (7–9h)', nivel: 'atencao' }

    }


    function atualizarSono() {

        let horas

        if (answers.sonoModo === 'intervalo') {

            horas = calcularIntervalo(answers.dormiu, answers.acordou)

        } else {

            horas = horasManuais

        }

        answers.horasSono = horas


        if (sleepHorasValor) {
            sleepHorasValor.textContent = formatarDuracao(horasManuais)
        }

        if (sleepMenos) sleepMenos.disabled = horasManuais <= SONO_MIN
        if (sleepMais) sleepMais.disabled = horasManuais >= SONO_MAX


        if (sleepResult && sleepResultText) {

            if (horas === null) {

                sleepResult.dataset.nivel = 'atencao'
                sleepResultText.textContent = 'Informe os dois horários'

            } else {

                const c = classificarSono(horas)

                sleepResult.dataset.nivel = c.nivel

                sleepResultText.textContent =
                    answers.sonoModo === 'intervalo'
                        ? 'Você dormiu ' + formatarDuracao(horas) + ' · ' + c.txt
                        : formatarDuracao(horas) + ' de sono · ' + c.txt

            }

        }

    }


    if (sleepModeEl) {

        sleepModeEl.addEventListener('click', event => {

            const button = event.target.closest('.sleep-mode-btn')

            if (!button) return

            sleepModeEl
                .querySelectorAll('.sleep-mode-btn')
                .forEach(b => b.setAttribute('aria-pressed', 'false'))

            button.setAttribute('aria-pressed', 'true')

            answers.sonoModo = button.dataset.mode

            if (sleepPanelHoras) sleepPanelHoras.hidden = answers.sonoModo !== 'horas'
            if (sleepPanelIntervalo) sleepPanelIntervalo.hidden = answers.sonoModo !== 'intervalo'

            atualizarSono()

        })

    }


    if (sleepMenos) {

        sleepMenos.addEventListener('click', () => {
            horasManuais = Math.max(SONO_MIN, horasManuais - SONO_PASSO)
            atualizarSono()
        })

    }


    if (sleepMais) {

        sleepMais.addEventListener('click', () => {
            horasManuais = Math.min(SONO_MAX, horasManuais + SONO_PASSO)
            atualizarSono()
        })

    }


    if (dormiuInput) {

        dormiuInput.addEventListener('input', event => {
            answers.dormiu = event.target.value
            atualizarSono()
        })

    }


    if (acordouInput) {

        acordouInput.addEventListener('input', event => {
            answers.acordou = event.target.value
            atualizarSono()
        })

    }


    atualizarSono()


    /* =====================================================
       PASSOS E ÁGUA
    ====================================================== */

    const passosInput =
        document.getElementById(
            'passosInput'
        )

    const aguaInput =
        document.getElementById(
            'aguaInput'
        )


    if (passosInput) {

        passosInput.addEventListener(
            'input',
            event => {

                answers.passos =
                    event.target.value ?
                        Number(
                            event.target.value
                        ) :
                        null

                updateProgress()

            }
        )

    }


    if (aguaInput) {

        aguaInput.addEventListener(
            'input',
            event => {

                answers.agua =
                    event.target.value ?
                        Number(
                            event.target.value
                        ) :
                        null

                updateProgress()

            }
        )

    }


    /* =====================================================
       PRESSÃO ARTERIAL (OPCIONAL)
    ====================================================== */

    const paToggle =
        document.getElementById(
            'paToggle'
        )

    const paFields =
        document.getElementById(
            'paFields'
        )

    const paSisInput =
        document.getElementById(
            'paSisInput'
        )

    const paDiaInput =
        document.getElementById(
            'paDiaInput'
        )


    if (paToggle) {

        paToggle.addEventListener(
            'change',
            event => {

                paFields.hidden =
                    !event.target.checked

                if (
                    !event.target.checked
                ) {

                    answers.paSis =
                        null

                    answers.paDia =
                        null

                    paSisInput.value =
                        ''

                    paDiaInput.value =
                        ''

                }

            }
        )

    }


    if (paSisInput) {

        paSisInput.addEventListener(
            'input',
            event => {

                answers.paSis =
                    event.target.value ?
                        Number(
                            event.target.value
                        ) :
                        null

            }
        )

    }


    if (paDiaInput) {

        paDiaInput.addEventListener(
            'input',
            event => {

                answers.paDia =
                    event.target.value ?
                        Number(
                            event.target.value
                        ) :
                        null

            }
        )

    }


    /* =====================================================
       GLICEMIA (SOMENTE QUEM TEM DIABETES)
    ====================================================== */

    const glicemiaQuestion =
        document.getElementById(
            'glicemiaQuestion'
        )

    const glicemiaInput =
        document.getElementById(
            'glicemiaInput'
        )


    if (
        glicemiaQuestion &&
        temDiabetes
    ) {

        glicemiaQuestion.hidden =
            false

    }


    if (glicemiaInput) {

        glicemiaInput.addEventListener(
            'input',
            event => {

                answers.glicemia =
                    event.target.value ?
                        Number(
                            event.target.value
                        ) :
                        null

                updateProgress()

            }
        )

    }


    /* =====================================================
       MODAL
    ====================================================== */

    function openModal() {
        if (!validarContextoBeneficiario()) return

        modal.hidden =
            false

        flow.hidden =
            false

        success.hidden =
            true

        document.body.style.overflow =
            'hidden'

    }


    function closeModal() {
        if (checkinEmAndamento) return

        modal.hidden =
            true

        document.body.style.overflow =
            ''

    }


    actionCard.addEventListener(
        'click',
        () => {

            if (
                actionCard
                    .classList
                    .contains(
                        'done'
                    )
            ) {

                return

            }


            openModal()

        }
    )


    document
        .getElementById(
            'ciBack'
        )
        .addEventListener(
            'click',
            closeModal
        )


    document
        .getElementById(
            'scrim'
        )
        .addEventListener(
            'click',
            closeModal
        )


    /* =====================================================
       CONCLUI CHECK-IN
    ====================================================== */

    let mensagemConclusao = 'Check-in concluído · +50 pts'
    const mensagemSucesso = document.querySelector('#ciSuccess p')
    mensagemSucesso.setAttribute('role', 'status')
    mensagemSucesso.setAttribute('aria-live', 'polite')
    const botaoVoltarScore = document.getElementById('ciDone')
    const mensagemCheckin = document.querySelector('.checkin-top p')
    mensagemCheckin.setAttribute('role', 'status')
    mensagemCheckin.setAttribute('aria-live', 'polite')

    function obterBeneficiarioCheckin() {
        return DaijiSession.obterIdBeneficiario()
    }

    function dataLocalCheckin() {
        const agora = new Date()
        return agora.getFullYear() + '-' +
            String(agora.getMonth() + 1).padStart(2, '0') + '-' +
            String(agora.getDate()).padStart(2, '0')
    }

    function validarContextoBeneficiario() {
        if (!DaijiSession.validar()) return false
        const idAtual = obterBeneficiarioCheckin()
        if (!contextoBeneficiarioInvalidado && idBeneficiarioPagina !== null &&
            idAtual === idBeneficiarioPagina) return true

        contextoBeneficiarioInvalidado = true
        const aviso = 'A sessão foi alterada ou está indisponível. Atualize ou reabra a página para continuar.'
        mensagemCheckin.textContent = aviso
        actionSub.textContent = aviso
        actionCard.disabled = true
        updateProgress()
        return false
    }

    window.addEventListener('storage', event => {
        if (event.storageArea === localStorage &&
            (event.key === 'daijiSession' || event.key === 'idBeneficiario' || event.key === null)) {
            validarContextoBeneficiario()
        }
    })

    submit.addEventListener('click', async () => {
        if (!validarContextoBeneficiario()) return
        if (checkinEmAndamento || actionCard.classList.contains('done')) return

        const nivelEstresse = Number(answers.estresse)
        // Converte somente o payload; mantém os rótulos e as respostas visuais.
        const qualidadeSono = {
            'Péssimo': 'RUIM', 'Regular': 'REGULAR', 'Bom': 'BOM', 'Ótimo': 'BOM'
        }[answers.sono]
        const qualidadeAlimentacao = {
            'Segui bem minha dieta': 'BOA', 'Alguns excessos': 'REGULAR', 'Não me alimentei bem': 'RUIM'
        }[answers.dieta]
        const respostaTexto = [
            'Sono: ' + answers.horasSono + 'h (' + answers.sonoModo + ')',
            answers.sonoModo === 'intervalo' ? 'Dormiu: ' + answers.dormiu + '; acordou: ' + answers.acordou : null,
            'Passos: ' + answers.passos,
            'Água: ' + answers.agua + ' copos',
            answers.paSis && answers.paDia ? 'Pressão: ' + answers.paSis + '/' + answers.paDia : null,
            temDiabetes ? 'Glicemia: ' + answers.glicemia + ' mg/dL; ' + answers.medicaoGlicemia : null,
            answers.remedio ? 'Medicação: ' + answers.remedio : null
        ].filter(Boolean).join(' | ')
        const encoder = new TextEncoder()
        if (!required.every(key => answers[key] !== null && answers[key] !== '') ||
            answers.horasSono === null || !Number.isFinite(answers.horasSono) ||
            [...flow.querySelectorAll('input')].some(input => !input.closest('[hidden]') && !input.checkValidity()) ||
            typeof qualidadeSono !== 'string' || typeof qualidadeAlimentacao !== 'string' ||
            !Number.isInteger(nivelEstresse) || nivelEstresse < 1 || nivelEstresse > 5 ||
            encoder.encode(answers.sono).length > 20 ||
            encoder.encode(answers.dieta).length > 30 ||
            encoder.encode(respostaTexto).length > 500) {
            mensagemCheckin.textContent = 'Responda todas as perguntas obrigatórias com valores válidos.'
            return
        }

        // O destino permanece vinculado à página, nunca a um novo usuário no storage.
        const idBeneficiario = idBeneficiarioPagina

        const dados = {
            nivelEstresse,
            qualidadeSono,
            qualidadeAlimentacao,
            humor: null,
            respostaTexto: respostaTexto || null
        }
        const data = dataLocalCheckin()
        checkinEmAndamento = true
        updateProgress()
        mensagemCheckin.textContent = 'Enviando seu check-in...'
        submit.setAttribute('aria-busy', 'true')

        try {
            let resposta
            try {
                resposta = await DaijiHttp.request(`/api/beneficiarios/${idBeneficiario}/checkins`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(dados)
                }, false)
            } catch (erro) {
                mensagemCheckin.textContent = DaijiHttp.isTimeout(erro)
                    ? 'Não foi possível confirmar o check-in a tempo. Ele pode ter sido registrado; uma nova tentativa pode duplicá-lo.'
                    : 'Serviço indisponível. Verifique a conexão e tente novamente.'
                return
            }

            if (resposta.status !== 201) {
                mensagemCheckin.textContent = resposta.status === 400
                    ? 'Dados inválidos. Confira suas respostas e tente novamente.'
                    : resposta.status === 404
                        ? 'Beneficiário não encontrado. Faça login novamente.'
                        : 'Não foi possível registrar o check-in. Tente novamente mais tarde.'
                return
            }

            flow.hidden = true
            success.hidden = false
            markDone()
            saveState(idBeneficiario, data)
            mensagemSucesso.textContent = 'Check-in registrado. Atualizando seu score...'
            botaoVoltarScore.disabled = true

            try {
                if (!validarContextoBeneficiario()) throw new Error('Contexto de beneficiário alterado')
                const recalculo = await DaijiHttp.request(`/api/beneficiarios/${idBeneficiario}/score/recalcular`, {
                    method: 'POST'
                }, false)
                if (recalculo.status !== 201) throw new Error('Falha no recálculo')

                // Aguarda a consulta inicial para que ela não sobrescreva o novo score.
                await consultaInicialScore
                const atualizado = await carregarScore(true, idBeneficiario)
                if (!atualizado) throw new Error('Falha ao atualizar score')

                mensagemConclusao = 'Check-in registrado e score atualizado · +50 pts'
                mensagemSucesso.textContent = 'Seu check-in foi registrado e seu score foi atualizado.'
            } catch {
                mensagemConclusao = 'Check-in registrado, mas não foi possível atualizar o score neste momento.'
                mensagemSucesso.textContent = mensagemConclusao
            } finally {
                botaoVoltarScore.disabled = false
            }
            showToast(mensagemConclusao)
        } finally {
            checkinEmAndamento = false
            submit.removeAttribute('aria-busy')
            updateProgress()
        }
    })


    function markDone() {

        if (
            !actionCard
                .classList
                .contains(
                    'done'
                )
        ) {

            const ganhos =
                calcPointsEarned()


            animatePoints(
                points,
                points + ganhos
            )


            points +=
                ganhos


            const successPts =
                document.getElementById(
                    'ciSuccessPts'
                )


            if (successPts) {

                successPts.textContent =
                    '+' +
                    ganhos +
                    ' pontos'

            }

        }


        actionCard
            .classList
            .add(
                'done'
            )


        actionTitle.textContent =
            'Check-in concluído'


        actionSub.textContent =
            'Muito bem! Volte amanhã · +50 pts ganhos'


        actionIc.innerHTML =

            '<i class="bi bi-check-lg"></i>'


        today
            .classList
            .add(
                'checked'
            )



    }


    /* =====================================================
       LOCAL STORAGE
    ====================================================== */

    function saveState(idBeneficiario, data) {
        try {
            // Apenas cache visual de um POST confirmado; não é histórico oficial.
            localStorage.setItem(STORE_KEY + ':' + idBeneficiario, JSON.stringify({
                idBeneficiario,
                data,
                done: true,
                points
            }))
        } catch {
            // Falha no cache não desfaz o check-in confirmado pelo servidor.
        }
    }

    function restoreState() {
        const idBeneficiario = idBeneficiarioPagina
        if (!idBeneficiario) return

        try {
            // A chave antiga, sem identificação e data, não é consultada.
            const saved = JSON.parse(localStorage.getItem(STORE_KEY + ':' + idBeneficiario) || 'null')
            if (!saved || saved.done !== true || saved.idBeneficiario !== idBeneficiario ||
                saved.data !== dataLocalCheckin()) return

            points = Number.isFinite(saved.points) ? saved.points : 1300 // Pontos demonstrativos.
            ptsNum.textContent = formatPoints(points)
            actionCard.classList.add('done')
            actionTitle.textContent = 'Check-in concluído'
            actionSub.textContent = 'Muito bem! Volte amanhã · +50 pts ganhos'
            actionIc.innerHTML = '<i class="bi bi-check-lg"></i>'
            today.classList.add('checked')
        } catch {
            // Cache ausente ou inválido não comprova conclusão.
        }
    }


    /* =====================================================
       SUCESSO
    ====================================================== */

    document
        .getElementById(
            'ciDone'
        )
        .addEventListener(
            'click',
            () => {

                if (checkinEmAndamento) return
                closeModal()
                showToast(mensagemConclusao)

            }
        )


    /* =====================================================
       TOAST
    ====================================================== */

    let toastTimer


    function showToast(message) {

        toastMsg.textContent =
            message


        toast
            .classList
            .add(
                'show'
            )


        clearTimeout(
            toastTimer
        )


        toastTimer =
            setTimeout(
                () => {

                    toast
                        .classList
                        .remove(
                            'show'
                        )

                },
                2600
            )

    }


    /* =====================================================
       INICIALIZAÇÃO
    ====================================================== */

    updateDate()

    restoreState()

    const consultaInicialScore = carregarScore()

    updateProgress()

})()
