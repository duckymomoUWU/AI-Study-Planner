import test from 'node:test'
import assert from 'node:assert/strict'
import {
  generateClientFallbackDraft,
  getGoalDraftStatus,
  setGoalDraftStatus,
  parseHour,
  parseDuration,
  findBestFitSlot,
  applyBestFitToDraftTasks,
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
  assert.equal(parseHour('2026-11-07', 9), 9) // Guard against parsing pure date string as 2026
  assert.equal(parseHour('2:30 PM'), 14.5)
  assert.equal(parseHour('9:00 AM'), 9)
  assert.equal(parseHour(null, 10), 10)
  assert.equal(parseHour(undefined, 8), 8)
  assert.equal(parseHour('invalid', 9), 9)
})

test('findBestFitSlot and applyBestFitToDraftTasks choose collision-free slots against existing tasks', () => {
  const existingTasks = [
    {
      id: 'ex-1',
      title: 'Học trên lớp',
      scheduledDate: '2026-11-09',
      recurringDays: [1], // Monday
      startHour: 9.0,
      durationHours: 2.0, // 9:00 - 11:00 busy
    },
    {
      id: 'ex-2',
      title: 'Ăn trưa',
      scheduledDate: '2026-11-09',
      recurringDays: [1], // Monday
      startHour: 11.5,
      durationHours: 1.5, // 11:30 - 13:00 busy
    },
  ]

  const proposedTask = {
    id: 'draft-1',
    title: 'Focus Time: Kiến trúc',
    scheduledDate: '2026-11-09',
    recurringDays: [1],
    startHour: 9.0, // Collides with ex-1!
    durationHours: 1.5,
  }

  const fit = findBestFitSlot(proposedTask, existingTasks)
  assert.equal(fit.isBestFit, true)
  assert.equal(fit.adjusted, true)
  // Should choose an open daytime candidate (e.g. 14.0)
  assert.ok(fit.startHour >= 8.0 && fit.startHour <= 18.0)
  assert.ok(fit.startHour < 9.0 - 1.5 || fit.startHour >= 11.0)
  assert.ok(fit.startHour < 11.5 - 1.5 || fit.startHour >= 13.0)

  // Multiple tasks placed sequentially without colliding with each other
  const draftList = [
    proposedTask,
    {
      id: 'draft-2',
      title: 'Focus Time: Thực hành',
      scheduledDate: '2026-11-09',
      recurringDays: [1],
      startHour: 9.0,
      durationHours: 1.5,
    },
  ]

  const fitted = applyBestFitToDraftTasks(draftList, existingTasks)
  assert.equal(fitted.length, 2)
  assert.notEqual(fitted[0].startHour, fitted[1].startHour)
  // No collision between fitted[0] and fitted[1]
  const end0 = fitted[0].startHour + fitted[0].durationHours
  const end1 = fitted[1].startHour + fitted[1].durationHours
  assert.ok(end0 <= fitted[1].startHour || end1 <= fitted[0].startHour)
})

test('parseDuration safely parses duration values with minimum threshold', () => {
  assert.equal(parseDuration(1.5), 1.5)
  assert.equal(parseDuration('1.5'), 1.5)
  assert.equal(parseDuration('01:30'), 1.5)
  assert.equal(parseDuration(0.1), 0.25) // clamped to minimum 0.25
  assert.equal(parseDuration(null, 2), 2)
  assert.equal(parseDuration('invalid', 1.5), 1.5)
})
