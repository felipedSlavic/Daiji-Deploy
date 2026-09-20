// Testes locais com navegador e API simulada. Não acessam o backend/Oracle.
// Usa Playwright disponível no ambiente de teste; não há dependência/build do app.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const http = require('node:http')
const vm = require('node:vm')
const { chromium } = require('playwright')
const root = path.resolve(__dirname, '../..')
const usuario = { idAutenticacao: 20, idBeneficiario: 7, nome: 'Pessoa de teste', email: 'pessoa@example.test', tipoUsuario: 'BENEFICIARIO' }
const score = { idScore: 2, idBeneficiario: 7, valorScore: 85, classificacaoRisco: 'BAIXO', dataCalculo: '2026-09-20T12:00:00' }
let total = 0

async function main() {
    await require('./http-timeout.cjs')()
    for (const file of fs.readdirSync(path.join(root, 'app/js')).filter(f => f.endsWith('.js'))) {
        new vm.Script(fs.readFileSync(path.join(root, 'app/js', file), 'utf8'), { filename: file })
    }
    console.log('Sintaxe: todos os scripts app/js válidos')
    const config = fs.readFileSync(path.join(root, 'app/js/api-config.js'), 'utf8')
    const configuredContext = { window: {} }
    vm.runInNewContext(config, configuredContext)
    const apiBase = configuredContext.window.DaijiApiConfig.baseUrl
    assert.equal(apiBase, 'https://daiji-deploy.onrender.com')
    for (const base of ['', 'https://backend.example.test/']) {
        const context = { window: {} }
        vm.runInNewContext(config.replace(/const BACKEND_URL = '[^']*'/, `const BACKEND_URL = '${base}'`), context)
        assert.equal(context.window.DaijiApiConfig.url('/api/auth/login'),
            (base ? base.slice(0, -1) : 'http://localhost:8080') + '/api/auth/login')
        assert.throws(() => context.window.DaijiApiConfig.url('https://outro.example.test'))
    }
    const server = http.createServer((req, res) => {
        const file = path.resolve(root, '.' + new URL(req.url, 'http://local').pathname)
        if (!file.startsWith(root + path.sep) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
            res.writeHead(404).end(); return
        }
        res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css', '.png': 'image/png' })[path.extname(file)] || 'application/octet-stream')
        res.end(fs.readFileSync(file))
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    const origin = `http://127.0.0.1:${server.address().port}`
    let browser
    try {
        browser = await chromium.launch({ channel: 'msedge', headless: true })
        async function scenario(name, action, handler, logged = false) {
            const context = await browser.newContext()
            const page = await context.newPage()
            const calls = [], errors = []
            page.on('pageerror', e => errors.push(e.message))
            await page.route('**/*', async route => {
                const req = route.request()
                if (req.url().startsWith(origin + '/')) return route.continue()
                if (req.url().startsWith(apiBase + '/api/')) {
                    const call = { path: new URL(req.url()).pathname, method: req.method(), body: req.postData() }
                    calls.push(call)
                    const reply = await handler(call)
                    if (reply.pending) return // Simula uma resposta que não chega; nunca acessa a rede.
                    if (reply.abort) return route.abort()
                    return route.fulfill({ status: reply.status ?? 200, contentType: 'application/json', body: reply.raw ?? JSON.stringify(reply.data), headers: { 'Access-Control-Allow-Origin': origin } })
                }
                return route.abort() // Fontes/CDNs e qualquer outro host: sem tráfego externo.
            })
            if (logged) await page.addInitScript(user => {
                sessionStorage.setItem('daijiSession', JSON.stringify({ versao: 2, logado: true, usuario: user }))
            }, usuario)
            try {
                await action(page, calls)
                assert.deepEqual(errors, [], 'Erros JavaScript na página')
                console.log('OK:', name); total++
            } finally { await context.close() }
        }
        async function login(page) {
            await page.goto(origin + '/app/login.html')
            await page.locator('#email').fill(usuario.email)
            await page.locator('#senha').fill(' senha de teste ')
            await page.locator('#loginForm').evaluate(form => form.requestSubmit())
        }
        await scenario('Login real, payload exato, sessão temporária e senha não persistida', async (page, calls) => {
            await login(page)
            await page.waitForURL('**/score.html')
            const session = await page.evaluate(() => JSON.parse(sessionStorage.getItem('daijiSession')))
            assert.deepEqual(session.usuario, usuario)
            assert.equal(await page.evaluate(() => localStorage.getItem('daijiSession')), null)
            assert.equal(await page.evaluate(() => sessionStorage.getItem('idBeneficiario')), '7')
            assert.deepEqual(JSON.parse(calls[0].body), { email: usuario.email, senha: ' senha de teste ' })
            assert.equal(calls[0].path, '/api/auth/login'); assert.equal(calls[0].method, 'POST')
            assert.equal(await page.evaluate(() => JSON.stringify([Object.entries(localStorage), Object.entries(sessionStorage)]).includes('senha de teste')), false)
        }, call => ({ data: call.path.endsWith('/login') ? usuario : score }))

        for (const response of [{ status: 401 }, { status: 503 }, { abort: true }, { raw: '{' }, { data: { email: usuario.email } }]) {
            await scenario('Login rejeita ' + JSON.stringify(response), async page => {
                await login(page)
                await page.waitForFunction(() => !document.querySelector('[type="submit"]').disabled)
                assert.equal(await page.evaluate(() => DaijiSession.estaAutenticado()), false)
                assert.ok(await page.locator('#loginMessage').textContent())
                assert.ok(page.url().endsWith('login.html'))
            }, () => response)
        }
        await scenario('Manter conectado, ID null e limpeza de ID legado', async page => {
            await page.goto(origin + '/app/login.html')
            await page.evaluate(() => {
                sessionStorage.setItem('idBeneficiario', '999')
                localStorage.setItem('idBeneficiario', '999')
            })
            await page.locator('.remember-wrapper').click()
            await page.locator('#email').fill(usuario.email)
            await page.locator('#senha').fill('senha de teste')
            await page.locator('#loginForm').evaluate(f => f.requestSubmit())
            await page.waitForURL('**/score.html')
            assert.equal(await page.evaluate(() => DaijiSession.obterIdBeneficiario()), null)
            assert.ok(await page.evaluate(() => localStorage.getItem('daijiSession')))
            assert.equal(await page.evaluate(() => sessionStorage.getItem('idBeneficiario') || localStorage.getItem('idBeneficiario')), null)
            assert.equal(await page.locator('.score-content h2').textContent(), 'Identificação necessária')
        }, () => ({ data: { ...usuario, idBeneficiario: null, tipoUsuario: 'GESTOR' } }))

        await scenario('Sessão antiga é rejeitada; páginas privadas redirecionam', async page => {
            await page.goto(origin + '/app/login.html')
            await page.evaluate(() => localStorage.setItem('daijiSession', JSON.stringify({ logado: true, usuario: { email: 'antigo@example.test' } })))
            await page.goto(origin + '/app/score.html')
            await page.waitForURL('**/login.html')
        }, () => { throw new Error('Não deveria consultar API') })

        async function cadastro(page) {
            await page.goto(origin + '/app/cadastro.html')
            await page.waitForFunction(() => !document.querySelector('#empresa').disabled)
            await page.locator('#empresa').selectOption('3')
            for (const [id, value] of Object.entries({ nome: usuario.nome, cpf: '123.456.789-09', celular: '(11) 95000-0000', email: usuario.email, nascimento: '2000-01-02', senha: 'senha teste', confirmarSenha: 'senha teste' })) {
                await page.locator('#' + id).fill(value)
            }
            await page.locator('#cadastroForm').evaluate(f => f.requestSubmit())
        }
        const empresas = { data: [{ idEmpresa: 3, nome: 'Empresa de teste' }] }
        await scenario('Cadastro 201, empresas reais, payload exato e sessão', async (page, calls) => {
            await cadastro(page)
            await page.waitForURL('**/personalizacao.html')
            assert.deepEqual(JSON.parse(calls.find(c => c.method === 'POST').body), {
                idEmpresa: 3, nome: usuario.nome, cpf: '12345678909', email: usuario.email,
                dataNascimento: '2000-01-02', telefoneWhatsapp: '11950000000', senha: 'senha teste'
            })
            assert.equal(calls.filter(c => c.method === 'POST').length, 1)
            assert.equal(await page.evaluate(() => DaijiSession.obterIdBeneficiario()), 7)
            await page.locator('[data-logout]').click()
            await page.waitForURL('**/login.html')
            assert.equal(await page.evaluate(() => DaijiSession.estaAutenticado()), false)
            assert.equal(await page.evaluate(() => sessionStorage.getItem('idBeneficiario')), null)
        }, call => call.method === 'GET' ? empresas : { status: 201, data: usuario })
        for (const status of [400, 409, 415, 503]) {
            await scenario('Cadastro trata HTTP ' + status, async page => {
                await cadastro(page)
                await page.waitForFunction(() => !document.querySelector('[type="submit"]').disabled)
                assert.equal(await page.evaluate(() => DaijiSession.estaAutenticado()), false)
                assert.ok(await page.locator('#cadastroMessage').textContent())
            }, call => call.method === 'GET' ? empresas : { status })
        }
        await scenario('Cadastro confirmado com JSON inválido não é reenviado', async (page, calls) => {
            await cadastro(page)
            await page.waitForFunction(() => document.querySelector('#cadastroMessage').textContent.includes('Cadastro realizado'))
            await page.locator('#cadastroForm').evaluate(f => f.dispatchEvent(new Event('submit', { cancelable: true })))
            assert.equal(calls.filter(c => c.method === 'POST').length, 1)
            assert.equal(await page.evaluate(() => DaijiSession.estaAutenticado()), false)
        }, call => call.method === 'GET' ? empresas : { status: 201, raw: '{' })
        await scenario('Empresas indisponíveis impedem cadastro', async page => {
            await page.goto(origin + '/app/cadastro.html')
            await page.waitForFunction(() => document.querySelector('#cadastroMessage').textContent.includes('Recarregue'))
            assert.equal(await page.locator('[type="submit"]').isDisabled(), true)
        }, () => ({ status: 503 }))

        await scenario('Falha de armazenamento não declara login concluído', async page => {
            await page.addInitScript(() => { Storage.prototype.setItem = () => { throw new Error('Storage bloqueado') } })
            await login(page)
            await page.waitForFunction(() => document.querySelector('#loginMessage').textContent.includes('Não foi possível iniciar'))
            assert.equal(await page.evaluate(() => DaijiSession.estaAutenticado()), false)
        }, () => ({ data: usuario }))
        await scenario('Login bloqueia envios simultâneos', async (page, calls) => {
            await login(page)
            await page.locator('#loginForm').evaluate(f => f.dispatchEvent(new Event('submit', { cancelable: true })))
            await page.waitForURL('**/score.html')
            assert.equal(calls.filter(c => c.path === '/api/auth/login').length, 1)
        }, async call => {
            await new Promise(resolve => setTimeout(resolve, 150))
            return { data: call.path.endsWith('/login') ? usuario : score }
        })

        for (const file of ['agendar', 'comunidade', 'descobrir', 'personalizacao']) {
            await scenario('Página privada ' + file + ' usa métodos existentes', async page => {
                await page.goto(origin + `/app/${file}.html`)
                assert.equal(await page.evaluate(() => DaijiSession.validar()), true)
            }, () => { throw new Error('Não existe API nessa página') }, true)
        }

        await scenario('Score 404, check-in 201, recálculo sem body e GET atualizado', async (page, calls) => {
            await page.goto(origin + '/app/score.html')
            await page.waitForFunction(() => document.querySelector('.score-content h2').textContent === 'Score ainda não calculado')
            await page.locator('#actionCard').click()
            for (const group of ['sono', 'estresse', 'dieta', 'remedio']) {
                await page.locator(`[data-group="${group}"] [data-val]`).first().click()
            }
            await page.locator('#ciSubmit').click()
            await page.waitForFunction(() => document.querySelector('#ciSuccess p').textContent.includes('seu score foi atualizado'))
            const checkin = calls.find(c => c.path.endsWith('/checkins'))
            const body = JSON.parse(checkin.body)
            assert.deepEqual(Object.keys(body).sort(), ['nivelEstresse', 'qualidadeSono', 'qualidadeAlimentacao', 'humor', 'respostaTexto'].sort())
            assert.ok(Number.isInteger(body.nivelEstresse) && body.nivelEstresse >= 1 && body.nivelEstresse <= 5)
            assert.equal(checkin.path, '/api/beneficiarios/7/checkins')
            const recalculo = calls.find(c => c.path.endsWith('/recalcular'))
            assert.equal(recalculo.method, 'POST'); assert.equal(recalculo.body, null)
            assert.equal(await page.locator('#scoreValue').textContent(), '85')
        }, call => {
            if (call.path.endsWith('/checkins')) return { status: 201, data: {} }
            if (call.path.endsWith('/recalcular')) return { status: 201, data: score }
            main.scoreGets = (main.scoreGets || 0) + 1
            return main.scoreGets === 1 ? { status: 404 } : { data: score }
        }, true)
        for (const etapa of ['checkins', 'recalcular', 'score']) {
            let gets = 0
            await scenario('Timeout em ' + etapa + ' sem retry e sem desfazer check-in 201', async (page, calls) => {
                await page.clock.install()
                await page.goto(origin + '/app/score.html')
                await page.waitForFunction(() => document.querySelector('#scoreValue').textContent === '85')
                await page.locator('#actionCard').click()
                for (const group of ['sono', 'estresse', 'dieta', 'remedio']) {
                    await page.locator(`[data-group="${group}"] [data-val]`).first().click()
                }
                const pendente = page.waitForRequest(req => req.url().endsWith('/' + etapa))
                await page.locator('#ciSubmit').click()
                await pendente
                await page.clock.fastForward(30001)
                if (etapa === 'checkins') {
                    await page.waitForFunction(() => document.querySelector('.checkin-top p').textContent.includes('pode ter sido registrado'))
                    assert.equal(calls.filter(c => c.method === 'POST').length, 1)
                    assert.equal(await page.locator('#actionCard').evaluate(el => el.classList.contains('done')), false)
                } else {
                    await page.waitForFunction(() => document.querySelector('#ciSuccess p').textContent.includes('Check-in registrado, mas'))
                    assert.equal(await page.locator('#actionCard').evaluate(el => el.classList.contains('done')), true)
                    assert.equal(await page.locator('#scoreValue').textContent(), '85')
                    await page.locator('#ciSubmit').evaluate(el => el.dispatchEvent(new Event('click')))
                    assert.equal(calls.filter(c => c.path.endsWith('/checkins')).length, 1)
                    assert.equal(calls.filter(c => c.path.endsWith('/recalcular')).length, 1)
                }
            }, call => {
                if (call.method === 'GET' && ++gets === 1) return { data: score }
                if (call.path.endsWith('/' + etapa)) return { pending: true }
                return { status: 201, data: {} }
            }, true)
        }
        const opcoesSono = { 'Péssimo': 'RUIM', 'Regular': 'REGULAR', 'Bom': 'BOM', 'Ótimo': 'BOM' }
        const opcoesDieta = { 'Segui bem minha dieta': 'BOA', 'Alguns excessos': 'REGULAR', 'Não me alimentei bem': 'RUIM' }
        // Respostas simuladas; os mesmos resultados são verificados no backend real com H2.
        const resultados = { RUIM: [95, 95, 90], REGULAR: [100, 95, 95], BOM: [100, 100, 95] }
        for (const [sonoVisual, sonoApi] of Object.entries(opcoesSono)) {
            for (const [dietaVisual, dietaApi] of Object.entries(opcoesDieta)) {
                const valorScore = resultados[sonoApi][Object.keys(opcoesDieta).indexOf(dietaVisual)]
                await scenario('Payload canônico: ' + sonoVisual + ' / ' + dietaVisual, async (page, calls) => {
                    await page.goto(origin + '/app/score.html')
                    await page.waitForFunction(valor => document.querySelector('#scoreValue').textContent === String(valor), valorScore)
                    for (const [group, options] of [['sono', opcoesSono], ['dieta', opcoesDieta]]) {
                        assert.deepEqual(await page.locator(`[data-group="${group}"] [data-val]`).evaluateAll(els => els.map(el => el.dataset.val)), Object.keys(options))
                    }
                    await page.locator('#actionCard').click()
                    for (const [group, value] of Object.entries({ sono: sonoVisual, estresse: '1', dieta: dietaVisual, remedio: 'Não tomei hoje' })) {
                        await page.locator(`[data-group="${group}"] [data-val="${value}"]`).click()
                    }
                    await page.locator('#ciSubmit').click()
                    await page.waitForFunction(() => document.querySelector('#ciSuccess p').textContent.includes('seu score foi atualizado'))
                    assert.deepEqual(JSON.parse(calls.find(c => c.path.endsWith('/checkins')).body), {
                        nivelEstresse: 1, qualidadeSono: sonoApi, qualidadeAlimentacao: dietaApi,
                        humor: null, respostaTexto: 'Horário de dormir: 22:30 | Medicação: Não tomei hoje'
                    })
                    assert.equal(calls.filter(c => c.path.endsWith('/checkins')).length, 1)
                    assert.equal(await page.locator('#scoreValue').textContent(), String(valorScore))
                }, call => ({ status: call.method === 'POST' ? 201 : 200, data: { ...score, valorScore } }), true)
            }
        }
        console.log(`PASS: ${total} cenários de navegador + sintaxe e configuração. API inteiramente simulada.`)
    } finally {
        if (browser) await browser.close()
        await new Promise(resolve => server.close(resolve))
    }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
