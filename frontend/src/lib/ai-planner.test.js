import test from 'node:test'
import assert from 'node:assert/strict'
import {
  generateClientFallbackDraft,
  getGoalDraftStatus,
  setGoalDraftStatus,
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
