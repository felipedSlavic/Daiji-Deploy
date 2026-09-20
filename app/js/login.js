document.addEventListener('DOMContentLoaded', function () {

    // =====================================================
    // ELEMENTOS
    // =====================================================

    const loginForm =
        document.querySelector('#loginForm')

    const campoEmail =
        document.querySelector('#email')

    const campoSenha =
        document.querySelector('#senha')

    const lembrar =
        document.querySelector('#lembrar')

    const loginMessage =
        document.querySelector('#loginMessage')

    const togglePassword =
        document.querySelector('#togglePassword')

    const passwordIcon =
        document.querySelector('#passwordIcon')


    // =====================================================
    // MOSTRAR / OCULTAR SENHA
    // =====================================================

    if (togglePassword && campoSenha) {

        togglePassword.addEventListener(
            'click',
            function () {

                const visivel =
                    campoSenha.type === 'text'


                campoSenha.type =
                    visivel
                        ? 'password'
                        : 'text'


                togglePassword.setAttribute(
                    'aria-label',
                    visivel
                        ? 'Mostrar senha'
                        : 'Ocultar senha'
                )


                if (passwordIcon) {

                    passwordIcon.classList.toggle(
                        'bi-eye',
                        visivel
                    )

                    passwordIcon.classList.toggle(
                        'bi-eye-slash',
                        !visivel
                    )

                }

            }
        )

    }


    // =====================================================
    // LOGIN
    // =====================================================

    if (!loginForm) {
        return
    }


    let emAndamento = false
    const botaoEntrar = loginForm.querySelector('[type="submit"]')
    loginMessage?.setAttribute('role', 'status')
    loginMessage?.setAttribute('aria-live', 'polite')

    loginForm.addEventListener('submit', async function (event) {
        event.preventDefault()
        if (emAndamento) return
        if (!loginForm.checkValidity()) {
            loginForm.reportValidity()
            return
        }
        const email = campoEmail.value.trim()
        const senha = campoSenha.value // A senha deve chegar sem transformações ao backend.
        if (!email || !senha) {
            mostrarMensagem('Informe seu e-mail e sua senha.', 'erro')
            return
        }
        emAndamento = true
        botaoEntrar.disabled = true
        mostrarMensagem('Entrando...', '')
        let sucesso = false
        try {
            const resposta = await DaijiHttp.request('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, senha })
            })
            if (resposta.status === 401) {
                mostrarMensagem('E-mail ou senha inválidos.', 'erro')
                return
            }
            if (resposta.status !== 200) {
                mostrarMensagem('Não foi possível entrar. Tente novamente mais tarde.', 'erro')
                return
            }
            let usuario
            try { usuario = await resposta.json() } catch {
                mostrarMensagem('O serviço retornou uma resposta inválida.', 'erro')
                return
            }
            try { DaijiSession.criarSessao(usuario, lembrar?.checked === true) } catch {
                mostrarMensagem('Não foi possível iniciar a sessão. Confira a resposta do serviço e o armazenamento do navegador.', 'erro')
                return
            }
            sucesso = true
            campoSenha.value = ''
            mostrarMensagem('Login realizado com sucesso.', 'sucesso')
            setTimeout(() => { window.location.href = 'score.html' }, 400)
        } catch (erro) {
            mostrarMensagem(DaijiHttp.isTimeout(erro)
                ? 'O servidor demorou para responder. Tente novamente.'
                : 'Não foi possível conectar ao serviço. Tente novamente mais tarde.', 'erro')
        } finally {
            if (!sucesso) {
                emAndamento = false
                botaoEntrar.disabled = false
            }
        }
    })

    // =====================================================
    // MENSAGEM
    // =====================================================

    function mostrarMensagem(
        texto,
        tipo
    ) {

        if (!loginMessage) {
            return
        }


        loginMessage.textContent =
            texto


        loginMessage.classList.remove(
            'error',
            'success',
            'erro',
            'sucesso'
        )


        if (tipo === 'erro') {

            loginMessage.classList.add(
                'error'
            )

        }


        if (tipo === 'sucesso') {

            loginMessage.classList.add(
                'success'
            )

        }

    }

})