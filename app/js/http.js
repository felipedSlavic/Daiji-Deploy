(function () {
    const TIMEOUT_MS = 30000

    async function request(url, options = {}, lerCorpo = true) {
        const controller = new AbortController()
        let timer
        const limite = new Promise((resolve, reject) => {
            timer = setTimeout(() => {
                const erro = new Error('Tempo de resposta excedido')
                erro.name = 'TimeoutError'
                reject(erro)
                controller.abort()
            }, TIMEOUT_MS)
        })

        try {
            return await Promise.race([
                (async () => {
                    const resposta = await fetch(DaijiApiConfig.url(url), { ...options, signal: controller.signal })
                    // Inclui a leitura do JSON no prazo. Check-in/recálculo usam só o status.
                    const texto = lerCorpo && resposta.ok ? await resposta.text() : ''
                    return {
                        ok: resposta.ok,
                        status: resposta.status,
                        json: async () => JSON.parse(texto)
                    }
                })(),
                limite
            ])
        } finally {
            clearTimeout(timer)
        }
    }

    window.DaijiHttp = {
        request,
        isTimeout: erro => erro && erro.name === 'TimeoutError'
    }
})()
