import { test } from 'node:test'
import assert from 'node:assert/strict'
import { describeProgress, describeProgressError, getGoalProgress, ProgressApiError } from './progress.js'

const progress = (total, completed, extra = {}) => ({
  goalId: 'goal-1', totalTasks: total, completedTasks: completed, remainingTasks: total - completed,
  completionRate: total === 0 ? 0 : completed / total, ...extra,
})

test('getGoalProgress reads /api/v1/goals/{id}/progress and unwraps data', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals/goal%2F1/progress')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ data: progress(5, 2) })
  })
  const p = await getGoalProgress('goal/1')
  assert.equal(p.totalTasks, 5)
  assert.equal(p.completedTasks, 2)
})

test('getGoalProgress keeps status and code of an error envelope and rejects malformed data', async t => {
  t.mock.method(globalThis, 'fetch', async () =>
    Response.json({ error: { code: 'NOT_FOUND', message: 'Không tìm thấy mục tiêu.' } }, { status: 404 }))
  await assert.rejects(getGoalProgress('x'), error => {
    assert.ok(error instanceof ProgressApiError)
    assert.equal(error.status, 404)
    assert.equal(error.code, 'NOT_FOUND')
    return true
  })

  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: { totalTasks: 'many' } }))
  await assert.rejects(getGoalProgress('x'), error => error.status === 502)
})

test('describeProgress shows completed/total, the percentage and remaining tasks', () => {
  const d = describeProgress(progress(5, 2))
  assert.equal(d.label, '2/5')
  assert.equal(d.percent, 40)
  assert.equal(d.remaining, 3)
  assert.equal(d.isEmpty, false)
})

test('no tasks is 0/0 with 0 percent and never divides by zero', () => {
  const d = describeProgress(progress(0, 0))
  assert.equal(d.label, '0/0')
  assert.equal(d.percent, 0)
  assert.equal(d.isEmpty, true)
  assert.equal(Number.isNaN(d.percent), false)
})

test('all tasks done is 100 percent and rounding follows completed/total', () => {
  assert.equal(describeProgress(progress(4, 4)).percent, 100)
  assert.equal(describeProgress(progress(3, 1)).percent, 33)
  assert.equal(describeProgress(progress(3, 2)).percent, 67)
})

test('error messages: login needed, goal gone, others keep the server text', () => {
  assert.match(describeProgressError(new ProgressApiError('x', { status: 401 })), /đăng nhập/)
  assert.match(describeProgressError(new ProgressApiError('x', { status: 404 })), /Không tìm thấy mục tiêu/)
  assert.equal(describeProgressError(new ProgressApiError('Lỗi máy chủ', { status: 500 })), 'Lỗi máy chủ')
  assert.equal(describeProgressError(new Error('boom')), 'boom')
})
