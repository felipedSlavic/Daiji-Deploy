const formularioLogin = document.querySelector('#loginForm')

const mensagemLogin = document.querySelector('#loginMessage')


formularioLogin.addEventListener('submit', function (event) {

    event.preventDefault()


    if (!formularioLogin.checkValidity()) {

        formularioLogin.reportValidity()

        return

    }


    if (mensagemLogin) {

        mensagemLogin.textContent = 'Entrando...'

    }


    // marca que a pessoa está logada na Daiji (portão de acesso do Dr.Online)
    try {
        localStorage.setItem('daijiLogado', 'true')

        // identifica o usuário logado: usa o nome do cadastro feito
        // com este e-mail; se não houver, monta a partir do e-mail
        const email = document.querySelector('#email').value.trim().toLowerCase()
        const contas = JSON.parse(localStorage.getItem('daiji_contas') || '{}')

        const nomeDoEmail = email
            .split('@')[0]
            .split(/[._-]+/)
            .filter(Boolean)
            .map(p => p.charAt(0).toUpperCase() + p.slice(1))
            .join(' ')

        localStorage.setItem('daiji_usuario', JSON.stringify({
            nome: contas[email] || nomeDoEmail || 'Você',
            email: email
        }))
    } catch (erro) {
        // ambiente sem localStorage (aba privada, etc.) — segue mesmo assim
    }


    // se a pessoa foi mandada para cá por um botão que exige login
    // (ex.: Dr.Online), volta para o destino original depois de entrar
    const redirect =
        new URLSearchParams(window.location.search).get('redirect')


    window.location.href = redirect ? redirect : 'score.html'

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
