(function () {
    if (!DaijiSession.validar()) return

    /* =====================================================
       ELEMENTOS
    ====================================================== */

    const circles =
        document.getElementById(
            'circles'
        )

    const streakTitle =
        document.getElementById(
            'streakTitle'
        )

    const inviteButton =
        document.getElementById(
            'inviteButton'
        )

    const toast =
        document.getElementById(
            'toast'
        )

    const toastMessage =
        document.getElementById(
            'toastMessage'
        )


    let toastTimer


    /* =====================================================
       CÍRCULOS
    ====================================================== */

    if (circles) {

        circles.addEventListener(
            'click',
            event => {

                const button =
                    event.target.closest(
                        '.circle'
                    )


                if (!button) {

                    return

                }


                circles
                    .querySelectorAll(
                        '.circle'
                    )
                    .forEach(item => {

                        item.setAttribute(
                            'aria-pressed',
                            'false'
                        )

                        item.classList.remove(
                            'active'
                        )

                    })


                button.setAttribute(
                    'aria-pressed',
                    'true'
                )


                button.classList.add(
                    'active'
                )


                const circle =
                    button.dataset.circle


                updateCircleTitle(
                    circle
                )

            }
        )

    }


    /* =====================================================
       TÍTULO DO CÍRCULO
    ====================================================== */

    function updateCircleTitle(circle) {

        if (!streakTitle) {

            return

        }


        const labels = {

            familia:
                'Streaks da família',

            amigos:
                'Streaks dos amigos',

            trabalho:
                'Streaks do trabalho'

        }


        streakTitle.textContent =
            labels[circle] ||
            'Streaks'

    }


    /* =====================================================
       ANIMAÇÃO DAS BARRAS
    ====================================================== */

    function animateStreaks() {

        const bars =
            document.querySelectorAll(
                '.streak-progress i.on'
            )


        bars.forEach(
            (bar, index) => {

                bar.style.transform =
                    'scaleX(0)'

                bar.style.transformOrigin =
                    'left'

                bar.style.transition =
                    'transform 0.4s cubic-bezier(.2,.8,.2,1)'


                setTimeout(
                    () => {

                        bar.style.transform =
                            'scaleX(1)'

                    },
                    80 +
                    index * 22
                )

            }
        )

    }


    /* =====================================================
       CONVIDAR
    ====================================================== */

    if (inviteButton) {

        inviteButton.addEventListener(
            'click',
            () => {

                showToast(
                    'Convite da comunidade pronto para compartilhar'
                )

            }
        )

    }


    /* =====================================================
       MENSAGENS
    ====================================================== */

    document
        .querySelectorAll(
            '.message-button'
        )
        .forEach(button => {

            button.addEventListener(
                'click',
                () => {

                    showToast(
                        'Abrindo conversa'
                    )

                }
            )

        })


    /* =====================================================
       INCENTIVAR
    ====================================================== */

    const supportButton =
        document.querySelector(
            '.support-button'
        )


    if (supportButton) {

        supportButton.addEventListener(
            'click',
            () => {

                supportButton.innerHTML =

                    '<i class="bi bi-heart-fill"></i> Incentivo enviado'


                supportButton.disabled =
                    true


                showToast(
                    'Seu incentivo foi enviado'
                )

            }
        )

    }


    /* =====================================================
       TOAST
    ====================================================== */

    function showToast(message) {

        if (
            !toast ||
            !toastMessage
        ) {

            return

        }


        toastMessage.textContent =
            message


        toast.classList.add(
            'show'
        )


        clearTimeout(
            toastTimer
        )


        toastTimer =
            setTimeout(
                () => {

                    toast.classList.remove(
                        'show'
                    )

                },
                2400
            )

    }


    /* =====================================================
       INICIALIZAÇÃO
    ====================================================== */

    requestAnimationFrame(
        animateStreaks
    )

})()
