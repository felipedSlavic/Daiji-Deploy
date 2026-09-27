(function () {

    /* =====================================================
       CONFIGURAÇÕES
    ====================================================== */

    const STORE_KEY =
        'daiji_checkin'


    let points =
        1250

    let healthScore =
        74


    const temDiabetes =
        localStorage.getItem(
            'daiji_diabetes'
        ) ===
        'sim'


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

        const scoreValueEl =
            document.getElementById(
                'scoreValue'
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


        const finalScore =
            typeof score ===
                'number' ?
                score :
                healthScore


        arc.style.strokeDasharray =
            circumference


        arc.style.strokeDashoffset =
            circumference


        requestAnimationFrame(
            () => {

                setTimeout(
                    () => {

                        arc.style.strokeDashoffset =
                            circumference *
                            (
                                1 -
                                finalScore / 100
                            )

                    },
                    150
                )

            }
        )


        if (scoreValueEl) {

            scoreValueEl.textContent =
                finalScore

        }

    }


    /* =====================================================
       CÁLCULO DO SCORE DE SAÚDE A PARTIR DO CHECK-IN
    ====================================================== */

    function calcHealthScore() {

        let delta =
            0


        const sonoPontos = {

            'Péssimo': -6,

            'Regular': -1,

            'Bom': 3,

            'Ótimo': 5

        }

        delta +=
            sonoPontos[
                answers.sono
            ] ||
            0


        // duração do sono
        const horas =
            answers.horasSono

        if (typeof horas === 'number') {

            if (horas >= 7 && horas <= 9) {
                delta += 3
            } else if (horas < 5) {
                delta -= 4
            } else if (horas < 7) {
                delta -= 1
            } else {
                delta -= 1
            }

        }


        delta -=
            (
                Number(
                    answers.estresse
                ) ||
                3
            ) -
            3


        const dietaPontos = {

            'Segui bem minha dieta': 4,

            'Alguns excessos': 0,

            'Não me alimentei bem': -4

        }

        delta +=
            dietaPontos[
                answers.dieta
            ] ||
            0


        const remedioPontos = {

            'Sim, todos': 4,

            'Esqueci um ou dois': -1,

            'Não tomei hoje': -5

        }

        delta +=
            remedioPontos[
                answers.remedio
            ] ||
            0


        if (
            answers.passos !==
                null &&
            answers.passos >=
                6000
        ) {

            delta +=
                3

        }


        if (
            answers.agua !==
                null &&
            answers.agua >=
                6
        ) {

            delta +=
                2

        }


        if (
            answers.paSis &&
            answers.paDia &&
            (
                answers.paSis >
                    140 ||
                answers.paDia >
                    90
            )
        ) {

            delta -=
                3

        }


        if (
            temDiabetes &&
            answers.glicemia
        ) {

            if (
                answers.glicemia >
                    180 ||
                answers.glicemia <
                    70
            ) {

                delta -=
                    4

            }

            else {

                delta +=
                    2

            }

        }


        const novoScore =
            Math.max(
                0,
                Math.min(
                    100,
                    healthScore +
                        delta
                )
            )


        return novoScore

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
            completed <
            required.length


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

    submit.addEventListener(
        'click',
        () => {

            if (
                submit.disabled
            ) {

                return

            }


            flow.hidden =
                true

            success.hidden =
                false


            markDone()

        }
    )


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


            healthScore =
                calcHealthScore()


            animateScore(
                healthScore
            )


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


        saveState()

    }


    /* =====================================================
       LOCAL STORAGE
    ====================================================== */

    function saveState() {

        try {

            localStorage.setItem(

                STORE_KEY,

                JSON.stringify({

                    done:
                        true,

                    answers:
                        answers,

                    points:
                        points,

                    healthScore:
                        healthScore

                })

            )

        }

        catch (error) {

            console.log(
                'Não foi possível salvar o check-in'
            )

        }

    }


    function restoreState() {

        try {

            const saved =
                JSON.parse(
                    localStorage.getItem(
                        STORE_KEY
                    ) ||
                    'null'
                )


            if (
                saved &&
                saved.done
            ) {

                points =
                    saved.points ||
                    1300


                healthScore =
                    saved.healthScore ||
                    healthScore


                ptsNum.textContent =
                    formatPoints(
                        points
                    )


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

        }

        catch (error) {

            console.log(
                'Não foi possível restaurar o check-in'
            )

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

                closeModal()

                showToast(
                    'Check-in concluído · +50 pts'
                )

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

    animateScore()

    updateProgress()

})()