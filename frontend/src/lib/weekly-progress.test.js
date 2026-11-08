import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  describeWeeklyError, describeWeeklyProgress, formatWeekRange, getWeeklyProgress, shiftWeek, startOfWeek,
  WeeklyProgressApiError,
} from './weekly-progress.js'

const week = (extra = {}) => ({
  weekStart: '2026-11-02', weekEnd: '2026-11-08', totalTasks: 7, completedTasks: 3, remainingTasks: 4,
  completionRate: 3 / 7,
  days: [
    { date: '2026-11-02', totalTasks: 2, completedTasks: 1 },
    { date: '2026-11-04', totalTasks: 5, completedTasks: 2 },
  ],
  ...extra,
})

test('weeks run Monday to Sunday: any day maps to its Monday', () => {
  assert.equal(startOfWeek('2026-11-02'), '2026-11-02') // Monday
  assert.equal(startOfWeek('2026-11-04'), '2026-11-02')
  assert.equal(startOfWeek('2026-11-08'), '2026-11-02') // Sunday belongs to the week that ends on it
  assert.equal(startOfWeek('2026-11-09'), '2026-11-09')
})

test('shiftWeek moves whole weeks across month and year boundaries', () => {
  assert.equal(shiftWeek('2026-11-02', 1), '2026-11-09')
  assert.equal(shiftWeek('2026-11-02', -1), '2026-10-26')
  assert.equal(shiftWeek('2026-12-28', 1), '2027-01-04')
  assert.equal(shiftWeek('2027-01-04', -1), '2026-12-28')
})

test('formatWeekRange shows the year once, or twice across a year change', () => {
  assert.equal(formatWeekRange('2026-11-02', '2026-11-08'), '02/11 – 08/11/2026')
  assert.equal(formatWeekRange('2026-12-28', '2027-01-03'), '28/12/2026 – 03/01/2027')
})

test('getWeeklyProgress requests /api/v1/progress/weekly?date= and unwraps data', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/progress/weekly?date=2026-11-04')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ data: week() })
  })
  const data = await getWeeklyProgress('2026-11-04')
  assert.equal(data.totalTasks, 7)
})

test('getWeeklyProgress without a date asks for the current week', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    assert.equal(url, '/api/v1/progress/weekly')
    return Response.json({ data: week() })
  })
  await getWeeklyProgress()
})

test('errors keep status and code; malformed data is rejected', async t => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ error: { code: 'VALIDATION_ERROR', message: 'Ngày không hợp lệ.' } }, { status: 400 }))
  await assert.rejects(getWeeklyProgress('x'), error => {
    assert.ok(error instanceof WeeklyProgressApiError)
    assert.equal(error.status, 400)
    assert.equal(error.code, 'VALIDATION_ERROR')
    return true
  })
  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: { totalTasks: 'many' } }))
  await assert.rejects(getWeeklyProgress('2026-11-02'), error => error.status === 502)
})

test('describeWeeklyProgress shows completed/total, percent, remaining and seven days from Monday', () => {
  const d = describeWeeklyProgress(week())
  assert.equal(d.label, '3/7')
  assert.equal(d.percent, 43)
  assert.equal(d.remaining, 4)
  assert.equal(d.isEmpty, false)
  assert.equal(d.range, '02/11 – 08/11/2026')
  assert.deepEqual(d.days.map(x => x.label), ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'])
  assert.deepEqual(d.days.map(x => x.date), [
    '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06', '2026-11-07', '2026-11-08'])
  assert.deepEqual([d.days[0].completed, d.days[0].total, d.days[0].percent], [1, 2, 50])
  assert.deepEqual([d.days[1].completed, d.days[1].total, d.days[1].percent], [0, 0, 0]) // day without data
  assert.equal(d.days[2].percent, 40)
})

test('a week without tasks is 0/0 with 0 percent, never NaN, and not an error', () => {
  const d = describeWeeklyProgress(week({ totalTasks: 0, completedTasks: 0, remainingTasks: 0, days: [] }))
  assert.equal(d.label, '0/0')
  assert.equal(d.percent, 0)
  assert.equal(d.isEmpty, true)
  assert.equal(Number.isNaN(d.percent), false)
  assert.equal(d.days.length, 7)
  assert.ok(d.days.every(day => day.total === 0 && day.percent === 0))
})

test('a fully done week is 100 percent', () => {
  assert.equal(describeWeeklyProgress(week({ totalTasks: 4, completedTasks: 4, remainingTasks: 0 })).percent, 100)
})

test('error messages: login needed, bad date, others keep the server text', () => {
  assert.match(describeWeeklyError(new WeeklyProgressApiError('x', { status: 401 })), /đăng nhập/)
  assert.match(describeWeeklyError(new WeeklyProgressApiError('x', { status: 400 })), /không hợp lệ/)
  assert.equal(describeWeeklyError(new WeeklyProgressApiError('Lỗi máy chủ', { status: 500 })), 'Lỗi máy chủ')
  assert.equal(describeWeeklyError(new Error('boom')), 'boom')
})
