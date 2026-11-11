import test from 'node:test'
import assert from 'node:assert/strict'
import { loadStudyWeek, offsetDay, visibleStudyDays, weekDays } from './studyWeek.js'

test('weeks run Monday through Sunday across month and year boundaries', () => {
  assert.deepEqual(weekDays('2026-11-10'), ['2026-11-09', '2026-11-10', '2026-11-11', '2026-11-12', '2026-11-13', '2026-11-14', '2026-11-15'])
  assert.equal(weekDays('2027-01-03')[0], '2026-12-28')
  assert.equal(weekDays('2027-01-03')[6], '2027-01-03')
  assert.equal(offsetDay('2026-12-31', 1), '2027-01-01')
})

test('weekly view loads all seven dates and propagates a failed day instead of showing an empty week', async () => {
  const seen = []
  const signal = new AbortController().signal
  const loaded = await loadStudyWeek('2026-11-10', { signal, load: async (date, options) => {
    assert.equal(options.signal, signal)
    seen.push(date)
    return { date, items: [{ id: date }] }
  } })
  assert.deepEqual(seen, weekDays('2026-11-10'))
  assert.equal(loaded.length, 7)
  await assert.rejects(loadStudyWeek('2026-11-10', { load: async date => {
    if (date === '2026-11-12') throw new Error('day unavailable')
    return { date, items: [] }
  } }), /day unavailable/)
})

test('goal filter recalculates duration and completion without changing stored tasks or hiding empty days', () => {
  const days = [{ date: '2026-11-10', items: [
    { goalId: 'a', estimatedMinutes: 30, completed: true },
    { goalId: 'b', estimatedMinutes: 45, completed: false },
  ] }, { date: '2026-11-11', items: [] }]
  const filtered = visibleStudyDays(days, 'a')
  assert.equal(filtered[0].minutes, 30)
  assert.equal(filtered[0].completed, 1)
  assert.equal(filtered[1].items.length, 0)
  assert.equal(days[0].items.length, 2)
  assert.equal(visibleStudyDays(days)[0].minutes, 75)
})
