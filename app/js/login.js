const formularioLogin = document.querySelector('#loginForm')
const mensagemLogin = document.querySelector('#loginMessage')
const botaoEntrar = formularioLogin.querySelector('[type="submit"]')
let loginEmAndamento = false
mensagemLogin.setAttribute('role', 'status')
mensagemLogin.setAttribute('aria-live', 'polite')

formularioLogin.addEventListener('submit', async function (event) {
    event.preventDefault()
    if (loginEmAndamento) return
    if (!formularioLogin.checkValidity()) { formularioLogin.reportValidity(); return }
    loginEmAndamento = true
    botaoEntrar.disabled = true
    mensagemLogin.textContent = 'Entrando...'
    let sucesso = false
    try {
        const email = document.querySelector('#email').value.trim().toLowerCase()
        const senha = document.querySelector('#senha').value
        const resposta = await DaijiHttp.request('/api/auth/login', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, senha })
        })
        if (resposta.status !== 200) {
            mensagemLogin.textContent = resposta.status === 401 ? 'E-mail ou senha inválidos.'
                : 'Não foi possível entrar. Tente novamente mais tarde.'
            return
        }
        try {
            DaijiSession.criarSessao(await resposta.json(), document.querySelector('#lembrar').checked)
        } catch {
            mensagemLogin.textContent = 'Não foi possível iniciar a sessão. Confira o armazenamento do navegador e tente novamente.'
            return
        }
        sucesso = true
        document.querySelector('#senha').value = ''
        const redirect = new URLSearchParams(window.location.search).get('redirect')
        let destino = new URL('score.html', window.location.href)
        if (redirect) {
            try {
                const solicitado = new URL(redirect, window.location.href)
                if (solicitado.origin === window.location.origin && /^https?:$/.test(solicitado.protocol)) destino = solicitado
            } catch { /* Destino inválido: segue para o score. */ }
        }
        window.location.href = destino.href
    } catch (erro) {
        mensagemLogin.textContent = DaijiHttp.isTimeout(erro)
            ? 'O servidor demorou para responder. Tente novamente.'
            : 'Não foi possível conectar ao serviço. Tente novamente mais tarde.'
    } finally {
        if (!sucesso) { loginEmAndamento = false; botaoEntrar.disabled = false }
    }
})

// ---------- Mostrar / ocultar senha ----------
const togglePassword = document.querySelector('#togglePassword')
const campoSenhaLogin = document.querySelector('#senha')
const passwordIcon = document.querySelector('#passwordIcon')

if (togglePassword && campoSenhaLogin) {

    togglePassword.addEventListener('click', function () {

        const visivel = campoSenhaLogin.type === 'text'

        campoSenhaLogin.type = visivel ? 'password' : 'text'

        togglePassword.setAttribute(
            'aria-label',
            visivel ? 'Mostrar senha' : 'Ocultar senha'
        )

        if (passwordIcon) {
            passwordIcon.classList.toggle('bi-eye', visivel)
            passwordIcon.classList.toggle('bi-eye-slash', !visivel)
        }

    })

}