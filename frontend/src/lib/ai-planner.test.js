import test from 'node:test'
import assert from 'node:assert/strict'
import {
  generateClientFallbackDraft,
  getGoalDraftStatus,
  setGoalDraftStatus,
  parseHour,
  parseDuration,
} from './ai-planner.js'

test('generateClientFallbackDraft generates repetitive weekly tasks within budget', () => {
  const goal = {
    id: 'test-goal-1',
    subject: 'Giải tích 2',
    topics: 'Tích phân đường, Tích phân mặt, Chuỗi số',
    startDate: '2026-11-10',
    examDate: '2026-12-05',
    minutesPerDay: 90,
  }

  const draft = generateClientFallbackDraft(goal)
  assert.equal(draft.goalId, 'test-goal-1')
  assert.ok(draft.proposal.tasks.length >= 3)

  for (const t of draft.proposal.tasks) {
    assert.ok(t.title.includes('Focus Time'))
    assert.equal(t.recurrence, 'weekly')
    assert.ok(Array.isArray(t.recurringDays))
    assert.ok(t.recurringDays.length > 0)
    assert.equal(t.source, 'ai')
    assert.equal(t.isDraft, true)
    assert.ok(t.startHour >= 8 && t.startHour <= 22)
  }
})

test('setGoalDraftStatus and getGoalDraftStatus manage status cleanly in localStorage', () => {
  // Mock localStorage in Node
  const storage = {}
  globalThis.localStorage = {
    getItem: (k) => storage[k] || null,
    setItem: (k, v) => { storage[k] = String(v) },
    removeItem: (k) => { delete storage[k] },
  }

  setGoalDraftStatus('g-1', { status: 'generating' })
  assert.deepEqual(getGoalDraftStatus('g-1'), { status: 'generating' })

  setGoalDraftStatus('g-1', { status: 'ready', taskCount: 4 })
  assert.equal(getGoalDraftStatus('g-1').status, 'ready')
  assert.equal(getGoalDraftStatus('g-1').taskCount, 4)

  setGoalDraftStatus('g-1', { status: 'confirmed' })
  assert.equal(getGoalDraftStatus('g-1').status, 'confirmed')

  setGoalDraftStatus('g-1', null)
  assert.equal(getGoalDraftStatus('g-1'), null)
})

test('parseHour safely parses decimal numbers, formatted string hours, and fallback defaults', () => {
  assert.equal(parseHour(9), 9)
  assert.equal(parseHour(9.5), 9.5)
  assert.equal(parseHour('9'), 9)
  assert.equal(parseHour('09:00'), 9)
  assert.equal(parseHour('09:30'), 9.5)
  assert.equal(parseHour('9h30'), 9.5)
  assert.equal(parseHour('14:15'), 14.25)
  assert.equal(parseHour('2026-05-18T09:30:00'), 9.5)
  assert.equal(parseHour('2026-05-18 14:00'), 14)
  assert.equal(parseHour(null, 10), 10)
  assert.equal(parseHour(undefined, 8), 8)
  assert.equal(parseHour('invalid', 9), 9)
})

test('parseDuration safely parses duration values with minimum threshold', () => {
  assert.equal(parseDuration(1.5), 1.5)
  assert.equal(parseDuration('1.5'), 1.5)
  assert.equal(parseDuration('01:30'), 1.5)
  assert.equal(parseDuration(0.1), 0.25) // clamped to minimum 0.25
  assert.equal(parseDuration(null, 2), 2)
  assert.equal(parseDuration('invalid', 1.5), 1.5)
})
