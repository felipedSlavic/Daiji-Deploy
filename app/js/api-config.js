(function () {
    // ÚNICO ponto de configuração: preencher com a URL pública do backend no deploy.
    // Vazio mantém o backend de desenvolvimento. Não incluir /api no final.
    const BACKEND_URL = ''
    const baseUrl = (BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '')

    window.DaijiApiConfig = Object.freeze({
        baseUrl,
        url(path) {
            if (typeof path !== 'string' || !path.startsWith('/api/')) {
                throw new Error('Caminho de API inválido.')
            }
            return baseUrl + path
        }
    })
})()
