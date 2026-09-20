const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')

// Relógio virtual: exercita o HTTP real sem rede e sem esperar 30 segundos.
module.exports = async function () {
    const source = fs.readFileSync(path.join(__dirname, '../js/http.js'), 'utf8')
    function setup(fetch) {
        let now = 0, next = 0
        const timers = new Map()
        const context = { window: {}, AbortController, fetch,
            DaijiApiConfig: { url: p => 'https://backend.example.test' + p },
            setTimeout(fn, ms) { const id = ++next; timers.set(id, { fn, at: now + ms }); return id },
            clearTimeout(id) { timers.delete(id) }
        }
        vm.runInNewContext(source, context)
        return { api: context.window.DaijiHttp, timers,
            advance(ms) { now += ms; for (const [id, timer] of timers) if (timer.at <= now) { timers.delete(id); timer.fn() } }
        }
    }
    let calls = 0, signal
    const timeout = setup((url, options) => { calls++; signal = options.signal; return new Promise(() => {}) })
    const pending = timeout.api.request('/api/beneficiarios/7/checkins', { method: 'POST' }, false)
    const rejected = assert.rejects(pending, e => timeout.api.isTimeout(e))
    timeout.advance(10001); assert.equal(signal.aborted, false)
    timeout.advance(19998); assert.equal(signal.aborted, false)
    timeout.advance(1); await rejected
    assert.equal(signal.aborted, true); assert.equal(calls, 1); assert.equal(timeout.timers.size, 0)

    let respond
    const slow = setup(() => new Promise(resolve => { respond = resolve }))
    const request = slow.api.request('/api/beneficiarios/7/checkins', { method: 'POST' }, false)
    slow.advance(15000)
    respond({ ok: true, status: 201, text() { throw new Error('Não deve ler body') } })
    assert.equal((await request).status, 201); assert.equal(slow.timers.size, 0)

    const body = setup(async () => ({ ok: true, status: 200, text: () => new Promise(() => {}) }))
    const reading = body.api.request('/api/beneficiarios/7/score')
    const bodyRejected = assert.rejects(reading, e => body.api.isTimeout(e))
    await Promise.resolve(); body.advance(30000); await bodyRejected
    assert.equal(body.timers.size, 0)
    console.log('OK: timeout de 30s, sucesso aos 15s, leitura do body, abort e nenhum retry de POST')
}
