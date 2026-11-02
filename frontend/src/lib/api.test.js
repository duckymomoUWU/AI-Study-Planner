import { test } from 'node:test'
import assert from 'node:assert/strict'
import { getBackendHealth } from './api.js'

test('health sends a relative request and accepts UP', async t => {
  const controller = new AbortController()
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/actuator/health')
    assert.equal(options.signal, controller.signal)
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ status: 'UP' })
  })
  assert.deepEqual(await getBackendHealth({ signal: controller.signal }), { status: 'UP' })
})

test('health rejects HTTP errors, unexpected payloads, DOWN and network failures', async t => {
  for (const response of [new Response(null, { status: 503 }), Response.json({}), Response.json({ status: 'DOWN' }), new Response('<html>fallback</html>')]) {
    const mock = t.mock.method(globalThis, 'fetch', async () => response)
    await assert.rejects(getBackendHealth())
    mock.mock.restore()
  }
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('Network error') })
  await assert.rejects(getBackendHealth(), /Network error/)
})
