import test from 'node:test'
import assert from 'node:assert/strict'
import { confirmPlanDraft, generatePlanDraft, getLatestPlanDraft, PlanApiError } from './ai-planner.js'
import { clearCsrfTokenCache } from './auth.js'

test('draft generation uses v1 and CSRF without creating or confirming tasks', async t => {
  clearCsrfTokenCache()
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push(url)
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'csrf' } })
    assert.equal(url, '/api/v1/goals/g%2F1/plan-drafts')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.method, 'POST')
    assert.deepEqual(JSON.parse(options.body), {})
    return Response.json({ data: { id: 'd1', goalVersion: 7, proposal: { tasks: [] } } }, { status: 201 })
  })
  assert.equal((await generatePlanDraft('g/1')).goalVersion, 7)
  assert.equal(calls.length, 2)
})

test('confirm retries preserve key, version and edited tasks, stripping client-only fields', async t => {
  const bodies = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals/g1/plan-drafts/d1/confirm')
    assert.equal(options.headers['Idempotency-Key'], 'key1')
    const body = JSON.parse(options.body)
    bodies.push(body)
    assert.deepEqual(body, { expectedGoalVersion: 7, tasks: [{ title: 'SQL', scheduledDate: '2026-11-10', estimatedMinutes: 30 }] })
    return Response.json({ data: { createdCount: 1 } }, { status: bodies.length === 1 ? 201 : 200 })
  })
  const items = [{ title: 'SQL', scheduledDate: '2026-11-10', estimatedMinutes: '30', source: 'manual', id: 'ui' }]
  await confirmPlanDraft('g1', 'd1', items, 7, 'key1')
  await confirmPlanDraft('g1', 'd1', items, 7, 'key1')
  assert.deepEqual(bodies[0], bodies[1])
})

test('AI and confirm errors stay errors, including expired, stale, auth, budget and network', async t => {
  for (const [status, code] of [[401, 'UNAUTHORIZED'], [409, 'GOAL_VERSION_CONFLICT'], [410, 'DRAFT_EXPIRED'], [422, 'DAILY_BUDGET_EXCEEDED'], [503, 'AI_UNAVAILABLE']]) {
    t.mock.method(globalThis, 'fetch', async () => Response.json({ error: { code, message: 'failure', context: { excessMinutes: 5 } } }, { status }))
    await assert.rejects(generatePlanDraft('g1'), error => error instanceof PlanApiError && error.status === status)
    await assert.rejects(confirmPlanDraft('g1', 'd1', [], 7, 'key1'), error => error.code === code && error.context.excessMinutes === 5)
  }
  t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('offline') })
  await assert.rejects(generatePlanDraft('g1'), /offline/)
  await assert.rejects(getLatestPlanDraft('g1'), /offline/)
})
