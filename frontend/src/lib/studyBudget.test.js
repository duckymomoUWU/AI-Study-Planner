import test from 'node:test'
import assert from 'node:assert/strict'
import { getStudyBudget, saveStudyBudget } from './studyBudget.js'
import { clearCsrfTokenCache } from './auth.js'

test('shared budget loads unfiltered all-goal usage for the selected dates', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/study-budget?from=2026-11-11&to=2026-11-17')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.method, 'GET')
    return Response.json({ data: { defaultMinutes: 120, dailyMinutes: {}, version: 1, days: [{ usedMinutes: 110, remainingMinutes: 10 }] } })
  })
  const value = await getStudyBudget('2026-11-11', '2026-11-17')
  assert.equal(value.days[0].usedMinutes, 110)
})

test('saving uses session, CSRF, expected version and preserves zero rest days', async t => {
  clearCsrfTokenCache()
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'csrf' } })
    assert.equal(options.method, 'PUT')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf')
    assert.deepEqual(JSON.parse(options.body), { expectedVersion: 2, defaultMinutes: 120, dailyMinutes: { '2026-11-12': 0, '2026-11-13': 60 } })
    return Response.json({ data: { defaultMinutes: 120, dailyMinutes: { '2026-11-12': 0 }, version: 3, days: [] } })
  })
  assert.equal((await saveStudyBudget({ version: 2 }, '120', { '2026-11-12': '0', '2026-11-13': '60' })).version, 3)
})

test('budget conflicts and invalid responses remain errors, never fake saved settings', async t => {
  for (const status of [401, 409, 422]) {
    t.mock.method(globalThis, 'fetch', async () => Response.json({ error: { code: 'BUDGET_ERROR', message: 'Rejected' } }, { status }))
    await assert.rejects(getStudyBudget(), error => error.status === status && error.code === 'BUDGET_ERROR')
  }
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: {} }))
  await assert.rejects(getStudyBudget(), /chưa hợp lệ/)
})
