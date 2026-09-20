const formulario = document.querySelector('#cadastroForm')
const senha = document.querySelector('#senha')
const confirmarSenha = document.querySelector('#confirmarSenha')
const empresa = document.querySelector('#empresa')
const mensagem = document.querySelector('#cadastroMessage')
const continuar = formulario.querySelector('[type="submit"]')
const empresasDisponiveis = new Set()
let emAndamento = false

function mostrarCadastro(texto) { mensagem.textContent = texto }

async function carregarEmpresas() {
    continuar.disabled = true
    try {
        const resposta = await DaijiHttp.request('/api/empresas')
        if (resposta.status !== 200) throw new Error('Empresas indisponíveis')
        const empresas = await resposta.json()
        if (!Array.isArray(empresas) || !empresas.length || empresas.some(item =>
            !item || !Number.isSafeInteger(item.idEmpresa) || item.idEmpresa <= 0 ||
            typeof item.nome !== 'string' || !item.nome.trim())) throw new Error('Lista inválida')
        empresa.replaceChildren(new Option('Selecione sua empresa', ''))
        for (const item of empresas) {
            empresasDisponiveis.add(item.idEmpresa)
            empresa.add(new Option(item.nome, String(item.idEmpresa)))
        }
        empresa.disabled = false
        continuar.disabled = false
    } catch {
        empresa.replaceChildren(new Option('Empresas indisponíveis', ''))
        mostrarCadastro('Não foi possível carregar as empresas. Recarregue a página para tentar novamente.')
    }
}

formulario.addEventListener('submit', async function (event) {
    event.preventDefault()
    if (emAndamento || empresa.disabled) return
    confirmarSenha.setCustomValidity(senha.value === confirmarSenha.value ? '' : 'As senhas devem ser iguais')
    if (!formulario.checkValidity()) {
        formulario.reportValidity()
        return
    }
    const idEmpresa = Number(empresa.value)
    if (!empresasDisponiveis.has(idEmpresa)) {
        mostrarCadastro('Selecione uma empresa válida.')
        return
    }
    const dados = {
        idEmpresa,
        nome: document.querySelector('#nome').value.trim(),
        cpf: document.querySelector('#cpf').value.replace(/\D/g, ''),
        email: document.querySelector('#email').value.trim(),
        dataNascimento: document.querySelector('#nascimento').value,
        telefoneWhatsapp: document.querySelector('#celular').value.replace(/\D/g, ''),
        senha: senha.value
    }
    emAndamento = true
    continuar.disabled = true
    let cadastrado = false
    mostrarCadastro('Realizando cadastro...')
    try {
        const resposta = await DaijiHttp.request('/api/auth/cadastro', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(dados)
        })
        if (resposta.status !== 201) {
            const erros = {
                400: 'Dados ou empresa inválidos. Confira o formulário.',
                409: 'CPF ou e-mail já cadastrado. Confira os dados ou faça login.',
                415: 'O serviço não aceitou o formato do cadastro.'
            }
            mostrarCadastro(erros[resposta.status] || 'Não foi possível cadastrar. Tente novamente mais tarde.')
            return
        }
        cadastrado = true // Não repetir um cadastro já confirmado, mesmo se a sessão falhar.
        senha.value = ''
        confirmarSenha.value = ''
        try {
            DaijiSession.criarSessao(await resposta.json())
        } catch {
            mostrarCadastro('Cadastro realizado, mas não foi possível iniciar a sessão. Use o link Entrar para fazer login.')
            return
        }
        window.location.href = 'personalizacao.html'
    } catch (erro) {
        mostrarCadastro(DaijiHttp.isTimeout(erro)
            ? 'Não foi possível confirmar o cadastro a tempo. Antes de repetir, tente entrar com seus dados.'
            : 'Falha de conexão. Não foi possível confirmar o cadastro; tente entrar antes de repetir.')
    } finally {
        if (!cadastrado) {
            emAndamento = false
            continuar.disabled = false
        }
    }
})

for (const campo of [senha, confirmarSenha]) {
    campo.addEventListener('input', () => confirmarSenha.setCustomValidity(''))
}
carregarEmpresas()
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