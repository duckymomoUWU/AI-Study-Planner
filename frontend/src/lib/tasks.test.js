import { updateTask } from './tasks.js'
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  createTask, deleteTask, listOverdueTasks, listTasksByDate, setTaskCompleted, summarizeTasks, TaskApiError,
} from './tasks.js'

const task = (id, extra = {}) => ({
  id, goalId: 'goal-1', title: 'Ôn SQL', scheduledDate: '2026-11-10', estimatedMinutes: 30,
  completed: false, source: 'manual', createdAt: '2026-11-07T03:00:00Z', ...extra,
})
const meta = (page, totalPages) => ({ page, limit: 100, totalItems: 1, totalPages })

test('summarizeTasks counts completed, remaining and minutes; empty is 0/0', () => {
  const s = summarizeTasks([task('a', { completed: true }), task('b'), task('c', { estimatedMinutes: 45 })])
  assert.equal(s.totalCount, 3)
  assert.equal(s.completedCount, 1)
  assert.equal(s.remainingCount, 2)
  assert.equal(s.totalEstimatedMinutes, 105)
  const empty = summarizeTasks([])
  assert.deepEqual([empty.totalCount, empty.completedCount, empty.remainingCount], [0, 0, 0])
})

test('listTasksByDate requests /api/v1/tasks?date= and summarizes the real response', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/tasks?date=2026-11-10&page=1&limit=100')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ data: [task('a', { completed: true }), task('b')], meta: meta(1, 1) })
  })
  const day = await listTasksByDate('2026-11-10')
  assert.equal(day.date, '2026-11-10')
  assert.equal(day.completedCount, 1)
  assert.equal(day.remainingCount, 1)
  assert.equal(day.items.length, 2)
})

test('listTasksByDate follows every page and rejects a malformed response', async t => {
  t.mock.method(globalThis, 'fetch', async url =>
    url.includes('page=1')
      ? Response.json({ data: [task('a')], meta: meta(1, 2) })
      : Response.json({ data: [task('b')], meta: meta(2, 2) }))
  assert.equal((await listTasksByDate('2026-11-10')).totalCount, 2)

  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => Response.json({ items: [] }))
  await assert.rejects(listTasksByDate('2026-11-10'), error => error.status === 502)
})

test('setTaskCompleted PATCHes the explicit target state and returns the stored task', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals/goal-1/tasks/task-1')
    assert.equal(options.method, 'PATCH')
    assert.equal(options.credentials, 'same-origin')
    assert.deepEqual(JSON.parse(options.body), { completed: true })
    return Response.json({ data: task('task-1', { completed: true }) })
  })
  const stored = await setTaskCompleted('goal-1', 'task-1', true)
  assert.equal(stored.completed, true)
})

test('setTaskCompleted sends false to re-open a task and encodes the IDs', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals/g%2F1/tasks/t%2F1')
    assert.deepEqual(JSON.parse(options.body), { completed: false })
    return Response.json({ data: task('t/1') })
  })
  assert.equal((await setTaskCompleted('g/1', 't/1', false)).completed, false)
})

test('setTaskCompleted surfaces server errors instead of faking success', async t => {
  for (const [status, code] of [[404, 'NOT_FOUND'], [401, 'UNAUTHORIZED'], [403, 'CSRF_INVALID']]) {
    t.mock.method(globalThis, 'fetch', async () => Response.json({ error: { code, message: 'x' } }, { status }))
    await assert.rejects(setTaskCompleted('goal-1', 'task-1', true), error => {
      assert.ok(error instanceof TaskApiError)
      assert.equal(error.status, status)
      assert.equal(error.code, code)
      return true
    })
    t.mock.restoreAll()
  }
})

test('createTask and deleteTask use the v1 paths and never fall back to demo data', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ error: { code: 'METHOD_NOT_ALLOWED', message: 'x' } }, { status: 405 }))
  await assert.rejects(createTask('goal-1', { title: 'A', scheduledDate: '2026-11-10', estimatedMinutes: 30 }),
    error => error.status === 405)
  await assert.rejects(deleteTask('goal-1', 'task-1'), error => error.status === 405)
})

test('overdue and update use real v1 endpoints and propagate failures', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/tasks/overdue') return Response.json({ data: [task('old')] })
    assert.equal(url, '/api/v1/tasks/task-1')
    assert.equal(options.method, 'PATCH')
    assert.deepEqual(JSON.parse(options.body), { title: 'Changed', estimatedMinutes: 60 })
    return Response.json({ data: task('task-1', { title: 'Changed' }) })
  })
  assert.equal((await listOverdueTasks())[0].id, 'old')
  assert.equal((await updateTask('task-1', { title: 'Changed', estimatedMinutes: 60 })).title, 'Changed')
  t.mock.method(globalThis, 'fetch', async () => Response.json({ error: { code: 'DAILY_BUDGET_EXCEEDED' } }, { status: 422 }))
  await assert.rejects(updateTask('task-1', { estimatedMinutes: 480 }), error => error.status === 422)
})
