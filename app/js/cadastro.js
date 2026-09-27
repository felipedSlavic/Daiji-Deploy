const formulario = document.querySelector('#cadastroForm')

const senha = document.querySelector('#senha')

const confirmarSenha = document.querySelector('#confirmarSenha')


formulario.addEventListener('submit', function (event) {

    event.preventDefault()


    if (!formulario.checkValidity()) {

        formulario.reportValidity()

        return

    }


    if (senha.value !== confirmarSenha.value) {

        confirmarSenha.setCustomValidity(
            'As senhas devem ser iguais'
        )

        confirmarSenha.reportValidity()

        return

    }


    confirmarSenha.setCustomValidity('')


    // quem acaba de se cadastrar já entra logado na Daiji
    try {
        localStorage.setItem('daijiLogado', 'true')

        // guarda quem é o usuário logado (usado na Comunidade etc.)
        const nome = document.querySelector('#nome').value.trim()
        const emailCampo = document.querySelector('#email')
        const email = emailCampo ? emailCampo.value.trim().toLowerCase() : ''

        const contas = JSON.parse(localStorage.getItem('daiji_contas') || '{}')
        if (email) contas[email] = nome
        localStorage.setItem('daiji_contas', JSON.stringify(contas))

        localStorage.setItem('daiji_usuario', JSON.stringify({ nome: nome, email: email }))
    } catch (erro) {
        // ambiente sem localStorage — segue mesmo assim
    }


    window.location.href = 'personalizacao.html'

})


confirmarSenha.addEventListener('input', function () {

    confirmarSenha.setCustomValidity('')

})

// ---------- Mostrar / ocultar senha (botões com data-toggle) ----------
document.querySelectorAll('.toggle-password[data-toggle]').forEach(function (botao) {

    botao.addEventListener('click', function () {

        const campo =
            document.getElementById(botao.getAttribute('data-toggle'))

        if (!campo) return

        const visivel = campo.type === 'text'

        campo.type = visivel ? 'password' : 'text'

        botao.setAttribute(
            'aria-label',
            visivel ? 'Mostrar senha' : 'Ocultar senha'
        )

        const icone = botao.querySelector('i')

        if (icone) {
            icone.classList.toggle('bi-eye', visivel)
            icone.classList.toggle('bi-eye-slash', !visivel)
        }

    })

})
