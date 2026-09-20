(function () {
    if (!DaijiSession.validar()) return

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

        type: 'Vídeo'

    }


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

            state.day !== null &&
            state.time !== null


        cta.disabled =
            !ready

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
