import test from 'node:test'
import assert from 'node:assert/strict'
import { overviewSummary, daysToExam } from './overview.js'

test('overview shows upcoming exams in date order and counts only unfinished minutes', () => {
  const goals = [
    { subject: 'Future', examDate: '2026-12-01' },
    { subject: 'Past', examDate: '2026-11-09' },
    { subject: 'Today', examDate: '2026-11-10' },
  ]
  const tasks = [{ completed: true, estimatedMinutes: 45 }, { completed: false, estimatedMinutes: 30 }]
  const summary = overviewSummary(goals, tasks, '2026-11-10')
  assert.deepEqual(summary.upcoming.map(goal => goal.subject), ['Today', 'Future'])
  assert.equal(summary.remaining, 1)
  assert.equal(summary.remainingMinutes, 30)
  assert.equal(summary.progress, 50)
  assert.equal(goals[0].subject, 'Future')
})

test('empty overview has no fabricated completion or invalid percentage', () => {
  const summary = overviewSummary([], [], '2026-11-10')
  assert.equal(summary.progress, 0)
  assert.equal(summary.total, 0)
  assert.equal(summary.remainingMinutes, 0)
})

test('exam countdown crosses months and years using date-only arithmetic', () => {
  assert.equal(daysToExam('2026-12-31', '2027-01-01'), 1)
  assert.equal(daysToExam('2026-11-10', '2026-11-10'), 0)
})
