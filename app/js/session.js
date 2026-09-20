// Sessão local de navegação. O backend não fornece token nem sessão persistente.
(function () {
    const SESSION_KEY = 'daijiSession'
    const PROFILE_KEY = 'daijiPerfil'
    const idValido = id => Number.isSafeInteger(id) && id > 0

    function respostaValida(usuario) {
        return Boolean(usuario && idValido(usuario.idAutenticacao) &&
            (usuario.idBeneficiario === null || idValido(usuario.idBeneficiario)) &&
            ['nome', 'email', 'tipoUsuario'].every(campo =>
                typeof usuario[campo] === 'string' && usuario[campo].trim().length > 0))
    }

    function obterSessao() {
        try {
            const dados = sessionStorage.getItem(SESSION_KEY) || localStorage.getItem(SESSION_KEY)
            const sessao = dados ? JSON.parse(dados) : null
            // Descarta sessões do protótipo que aceitava qualquer email/senha.
            return sessao?.versao === 2 && sessao.logado === true && respostaValida(sessao.usuario)
                ? sessao : null
        } catch { return null }
    }

    function estaAutenticado() { return obterSessao() !== null }
    function validar() {
        if (estaAutenticado()) return true
        window.location.replace('login.html')
        return false
    }

    function limparSessao() {
        for (const storage of [localStorage, sessionStorage]) {
            for (const chave of [SESSION_KEY, 'idBeneficiario', 'daijiLogado']) storage.removeItem(chave)
        }
    }

    function criarSessao(resposta, manterConectado = false) {
        if (!respostaValida(resposta)) throw new Error('Resposta de autenticação inválida.')
        // Lista explícita: nunca persistir senha, campos extras ou tokens.
        const { idAutenticacao, idBeneficiario, nome, email, tipoUsuario } = resposta
        const usuario = { idAutenticacao, idBeneficiario, nome, email, tipoUsuario }
        const sessao = { versao: 2, logado: true, usuario, criadoEm: new Date().toISOString() }
        try {
            limparSessao()
            const storage = manterConectado ? localStorage : sessionStorage
            storage.setItem(SESSION_KEY, JSON.stringify(sessao))
            if (idBeneficiario !== null) storage.setItem('idBeneficiario', String(idBeneficiario))
            storage.setItem('daijiLogado', 'true')
        } catch {
            try { limparSessao() } catch { /* Storage indisponível: não declarar sucesso. */ }
            throw new Error('Não foi possível salvar a sessão neste navegador.')
        }
        // Preserva a atualização do perfil visual, sem misturar contas.
        try {
            localStorage.setItem(PROFILE_KEY, JSON.stringify({ nome, email }))
        } catch { /* Perfil visual não é fonte de autenticação. */ }
    }

    function obterUsuario() { return obterSessao()?.usuario || null }
    function obterIdBeneficiario() { return obterUsuario()?.idBeneficiario ?? null }
    function logout() {
        try { limparSessao() } finally { window.location.href = 'login.html' }
    }

    window.DaijiSession = {
        obterSessao, estaAutenticado, validar, criarSessao,
        obterUsuario, obterIdBeneficiario, logout
    }
    document.addEventListener('click', event => {
        if (event.target.closest('[data-logout]')) {
            event.preventDefault()
            logout()
        }
    })
    if (document.currentScript?.hasAttribute('data-private')) validar()
})()
