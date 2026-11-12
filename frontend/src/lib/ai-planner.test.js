import test from 'node:test'
import assert from 'node:assert/strict'
import { confirmPlanDraft, generatePlanDraft, getLatestPlanDraft, getPendingPlanDrafts, reschedulePlanDraft, draftReview, draftState, PlanApiError, sortDraftTasksByPriority } from './ai-planner.js'
import { clearCsrfTokenCache } from './auth.js'

test('confirmed or expired history is read-only; stale scoped work can be rescheduled and valid drafts remain editable', () => {
  assert.equal(draftState({ status: 'CONFIRMED' }).readOnly, true)
  assert.equal(draftState({ status: 'CONFIRMED' }).canReschedule, false)
  assert.match(draftState({ status: 'CONFIRMED' }).message, /đã được lưu/)
  assert.equal(draftState({ status: 'EXPIRED' }).readOnly, true)
  assert.equal(draftState({ status: 'STALE', proposal: { scope: {} } }).canReschedule, true)
  assert.equal(draftState({ status: 'STALE', proposal: {} }).canReschedule, false)
  assert.equal(draftState({}).readOnly, false)
  assert.equal(draftState({ status: 'DRAFT' }).readOnly, false)
})

test('insufficient budget preserves honest estimates and distinguishes no-day-fits from total shortage', () => {
  const items = [{ id: 'a', estimatedMinutes: 90, scheduledDate: '2026-11-12' }, { id: 'b', estimatedMinutes: 90, scheduledDate: null }]
  const before = structuredClone(items)
  const review = draftReview(items, 120)
  assert.equal(review.requiredMinutes, 180)
  assert.equal(review.shortfallMinutes, 60)
  assert.equal(review.remainingMinutes, 90)
  assert.equal(review.scheduled.length, 1)
  assert.equal(draftReview(items, 200).shortfallMinutes, 0)
  assert.equal(draftReview(items, 200).remaining.length, 1)
  assert.deepEqual(items, before)
})

test('partial save explicitly retains edited deferred work; pending reload and reschedule use real API', async t => {
  clearCsrfTokenCache()
  const bodies = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'csrf' } })
    if (url.endsWith('/pending')) return Response.json({ data: [{ id: 'remaining' }] })
    if (url.endsWith('/reschedule')) {
      assert.equal(options.method, 'POST')
      assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf')
      return Response.json({ data: { id: 'remaining', goalVersion: 9 } })
    }
    bodies.push(JSON.parse(options.body))
    assert.equal(options.headers['Idempotency-Key'], 'retry-key')
    return Response.json({ data: { createdCount: 1, remainingCount: 1, remainingDraftId: 'remaining' } })
  })
  const scheduled = [{ id: 'a', topicId: 't', title: 'Practice basics', scheduledDate: '2026-11-12', estimatedMinutes: '90' }]
  const deferredTasks = [{ id: 'b', topicId: 't', title: 'Edited advanced work', estimatedMinutes: '110', reason: 'does not fit' }]
  for (let i = 0; i < 2; i++) await confirmPlanDraft('g', 'd', scheduled, 7, 'retry-key', { allowPartial: true, deferredTasks })
  assert.deepEqual(bodies[0], bodies[1])
  assert.equal(bodies[0].allowPartial, true)
  assert.deepEqual(bodies[0].deferredTasks, [{ id: 'b', topicId: 't', title: 'Edited advanced work', estimatedMinutes: 110 }])
  assert.equal((await getPendingPlanDrafts('g'))[0].id, 'remaining')
  assert.equal((await reschedulePlanDraft('g', 'remaining')).goalVersion, 9)
})

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


test('draft review sorts by priority without changing dates, minutes, equal-priority order or input', () => {
  const items = [
    { title: 'Revision', priority: 'LOW', scheduledDate: '2026-11-12', estimatedMinutes: 30 },
    { title: 'Practice', priority: 'MEDIUM', scheduledDate: '2026-11-11', estimatedMinutes: 30 },
    { title: 'Foundations', priority: 'HIGH', scheduledDate: '2026-11-10', estimatedMinutes: 30 },
    { title: 'Legacy', scheduledDate: '2026-11-11', estimatedMinutes: 15 },
  ]
  const original = structuredClone(items)
  const sorted = sortDraftTasksByPriority(items)
  assert.deepEqual(sorted.map(task => task.title), ['Foundations', 'Practice', 'Legacy', 'Revision'])
  assert.deepEqual(items, original)
  for (const task of sorted) assert.deepEqual(task, original.find(item => item.title === task.title))
})
