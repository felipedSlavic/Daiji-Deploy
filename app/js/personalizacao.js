(function () {
    if (!DaijiSession.validar()) return
    const formulario = document.querySelector('#personalizacaoForm')
    const mensagem = document.querySelector('#personalizacaoMessage')
    const botao = formulario.querySelector('[type="submit"]')
    const id = DaijiSession.obterIdBeneficiario()
    const chave = 'daiji_personalizacao:' + id
    let emAndamento = false
    try {
        const salvo = JSON.parse(localStorage.getItem(chave) || 'null')
        if (salvo) {
            document.querySelector('#doencas').value = salvo.doencas || ''
            document.querySelector('#medicacao').value = salvo.medicacao || ''
            for (const campo of ['diabetes', 'atividade']) {
                const valor = salvo[campo]
                if (['sim', 'nao'].includes(valor)) formulario.querySelector(`input[name="${campo}"][value="${valor}"]`).checked = true
            }
        }
    } catch { /* Preferências locais ausentes ou inválidas. */ }

    formulario.addEventListener('submit', async event => {
        event.preventDefault()
        if (emAndamento) return
        if (!DaijiSession.validar() || id === null || DaijiSession.obterIdBeneficiario() !== id) {
            mensagem.textContent = 'Faça login com sua conta de beneficiário e reabra esta página.'
            return
        }
        if (!formulario.checkValidity()) { formulario.reportValidity(); return }
        const dados = {
            doencas: document.querySelector('#doencas').value.trim(),
            medicacao: document.querySelector('#medicacao').value.trim(),
            diabetes: formulario.querySelector('input[name="diabetes"]:checked')?.value || 'nao',
            atividade: formulario.querySelector('input[name="atividade"]:checked')?.value || null
        }
        if (new TextEncoder().encode(dados.medicacao).length > 100) {
            mensagem.textContent = 'O nome da medicação está muito longo. Abrevie a descrição e tente novamente.'
            return
        }
        emAndamento = true
        botao.disabled = true
        mensagem.textContent = 'Salvando suas preferências...'
        try {
            if (dados.medicacao) {
                const caminho = `/api/beneficiarios/${id}/medicacoes`
                const resposta = await DaijiHttp.request(caminho)
                if (resposta.status !== 200) throw new Error('Consulta indisponível')
                const existentes = await resposta.json()
                if (!Array.isArray(existentes) || existentes.some(item =>
                    item.idBeneficiario !== id || typeof item.nomeMedicamento !== 'string')) throw new Error('Lista inválida')
                if (DaijiSession.obterIdBeneficiario() !== id) throw new Error('Sessão alterada')
                // O campo atual é texto livre: não inferir dosagem nem horário clínico.
                if (!existentes.some(item => item.nomeMedicamento.trim().toLowerCase() === dados.medicacao.toLowerCase())) {
                    const criado = await DaijiHttp.request(caminho, {
                        method: 'POST', headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ nomeMedicamento: dados.medicacao, dosagem: null, horarioPrevisto: null })
                    }, false)
                    if (criado.status !== 201) throw new Error('Cadastro não confirmado')
                }
            }
            if (DaijiSession.obterIdBeneficiario() !== id) throw new Error('Sessão alterada')
            // Sem endpoints de comorbidades/hábitos: apenas preferências por beneficiário.
            localStorage.setItem(chave, JSON.stringify(dados))
            window.location.href = 'score.html'
        } catch {
            mensagem.textContent = 'Não foi possível concluir agora. Seus campos foram mantidos. Tente novamente; as medicações cadastradas serão consultadas antes de um novo envio.'
        } finally {
            emAndamento = false
            botao.disabled = false
        }
    })
})()
