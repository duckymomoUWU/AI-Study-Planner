import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createTask, deleteTask, listOverdueTasks, listTasksByDate, toggleTaskComplete } from './tasks.js'

test('listTasksByDate calls /api/tasks?date=YYYY-MM-DD and returns response items', async t => {
  const mockPayload = {
    date: '2026-11-09',
    totalEstimatedMinutes: 120,
    completedCount: 1,
    totalCount: 2,
    items: [
      { id: 'task-1', title: 'Làm bài tập', completed: true, estimatedMinutes: 60 },
      { id: 'task-2', title: 'Đọc sách', completed: false, estimatedMinutes: 60 },
    ],
  }

  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/tasks?date=2026-11-09')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    return Response.json(mockPayload)
  })

  const result = await listTasksByDate('2026-11-09')
  assert.equal(result.items.length, 2)
  assert.equal(result.totalEstimatedMinutes, 120)
  assert.equal(result.isPlaceholder, false)
})

test('listTasksByDate falls back to placeholder items when backend returns 404 (endpoint pending)', async t => {
  t.mock.method(globalThis, 'fetch', async () => {
    return new Response(JSON.stringify({ message: 'Not found' }), { status: 404 })
  })

  const result = await listTasksByDate('2099-01-01')
  assert.ok(result.isPlaceholder)
  assert.ok(Array.isArray(result.items))
})

test('toggleTaskComplete sends PATCH /api/tasks/:id/complete with boolean payload', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/tasks/task-123/complete')
    assert.equal(options.method, 'PATCH')
    assert.equal(options.credentials, 'same-origin')
    assert.deepEqual(JSON.parse(options.body), { completed: true })
    return Response.json({ id: 'task-123', completed: true })
  })

  const result = await toggleTaskComplete('task-123', true)
  assert.equal(result.completed, true)
})

test('createTask sends POST /api/goals/:goalId/tasks with required fields', async t => {
  const input = { title: 'Làm đề thi thử', scheduledDate: '2026-11-09', estimatedMinutes: 90 }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/goals/goal-abc/tasks')
    assert.equal(options.method, 'POST')
    assert.equal(options.credentials, 'same-origin')
    assert.deepEqual(JSON.parse(options.body), input)
    return Response.json({ id: 'task-new', ...input }, { status: 201 })
  })

  const result = await createTask('goal-abc', input)
  assert.equal(result.id, 'task-new')
  assert.equal(result.estimatedMinutes, 90)
})

test('deleteTask sends DELETE /api/tasks/:id and succeeds with 204', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/tasks/task-to-delete')
    assert.equal(options.method, 'DELETE')
    return new Response(null, { status: 204 })
  })

  const success = await deleteTask('task-to-delete')
  assert.equal(success, true)
})

test('listOverdueTasks requests /api/tasks/overdue and returns items', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/tasks/overdue')
    assert.equal(options.method, 'GET')
    return Response.json({ items: [{ id: 'overdue-1', title: 'Task cũ', completed: false }] })
  })

  const items = await listOverdueTasks()
  assert.equal(items.length, 1)
  assert.equal(items[0].id, 'overdue-1')
})
