import test from 'node:test'
import assert from 'node:assert/strict'
import { preferenceExcessDays, confirmPlanDraft, reschedulePlanDraft } from './ai-planner.js'
import { clearCsrfTokenCache } from './auth.js'

test('preference warning includes saved work, edited estimates and per-day preferences', () => {
  const goal = { minutesPerDay: 120 }
  const draft = { proposal: { scope: { preferredDailyMinutes: { '2026-11-14': 120 }, existingGoalMinutes: { '2026-11-14': 60 } } } }
  const items = [{ scheduledDate: '2026-11-14', estimatedMinutes: '80' }, { scheduledDate: null, estimatedMinutes: 90 }]
  assert.deepEqual(preferenceExcessDays(items, draft, goal), [{ date: '2026-11-14', preferredMinutes: 120, plannedMinutes: 140, excessMinutes: 20 }])
  assert.equal(preferenceExcessDays([{ scheduledDate: '2026-11-14', estimatedMinutes: 60 }], draft, goal).length, 0)
})

test('explicit exceedance consent and strict reallocation reach the backend', async t => {
  clearCsrfTokenCache()
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'csrf' } })
    calls.push({ url, body: JSON.parse(options.body), key: options.headers['Idempotency-Key'] })
    return Response.json({ data: {} })
  })
  const tasks = [{ id: 'a', topicId: 't', title: 'Practice', estimatedMinutes: 140, scheduledDate: '2026-11-14' }]
  await confirmPlanDraft('g', 'd', tasks, 2, 'retry-key', { allowPreferenceExceedance: true })
  await confirmPlanDraft('g', 'd', tasks, 2, 'retry-key', { allowPreferenceExceedance: true })
  await reschedulePlanDraft('g', 'd', { respectPreferences: true })
  assert.equal(calls[0].body.allowPreferenceExceedance, true)
  assert.deepEqual(calls[0], calls[1])
  assert.deepEqual(calls[2].body, { respectPreferences: true })
})
