import test from 'node:test'
import assert from 'node:assert/strict'
import { clearCsrfTokenCache } from './auth.js'
import { completeSessionTask, loadSessionTask } from './sessionCompletion.js'

const session = { id: 'session', status: 'COMPLETED', goalId: 'goal', taskId: 'task' }
const task = { id: 'task', goalId: 'goal', title: 'Study', completed: false }
const path = '/api/v1/goals/goal/tasks/task'

test('only a saved completed session with live links can ask about a task; reading never completes it', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options }); return Response.json({ data: task })
  })
  for (const status of ['RUNNING', 'PAUSED', 'CANCELLED']) assert.equal(await loadSessionTask({ ...session, status }), null)
  assert.equal(await loadSessionTask({ ...session, taskId: null }), null)
  assert.equal(await loadSessionTask({ ...session, goalId: null }), null)
  assert.equal(calls.length, 0)
  assert.deepEqual(await loadSessionTask(session), task)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].url, path)
  assert.equal(calls[0].options.method, 'GET')
})

test('explicit confirmation marks the session task true with CSRF, never toggles or mutates the session', async t => {
  clearCsrfTokenCache()
  const writes = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'token' } })
    assert.equal(url, path)
    if (options.method === 'PATCH') {
      writes.push(options); return Response.json({ data: { ...task, completed: true } })
    }
    return Response.json({ data: task })
  })
  assert.equal((await completeSessionTask(session, { csrfToken: 'token' })).completed, true)
  assert.equal((await completeSessionTask(session, { csrfToken: 'token' })).completed, true)
  assert.equal(writes.length, 2)
  assert.equal(writes[0].headers['X-CSRF-TOKEN'], 'token')
  assert.deepEqual(JSON.parse(writes[0].body), { completed: true })
  assert.equal(writes[0].body, writes[1].body)
  clearCsrfTokenCache()
})

test('already completed or deleted tasks are not written, and failed confirmation stays an error', async t => {
  let mode = 'done'; let writes = 0
  clearCsrfTokenCache()
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'token' } })
    if (mode === 'deleted') return Response.json({ error: { code: 'NOT_FOUND' } }, { status: 404 })
    if (options.method === 'PATCH') { writes++; return Response.json({ error: { code: 'INTERNAL_SERVER_ERROR' } }, { status: 500 }) }
    return Response.json({ data: { ...task, completed: mode === 'done' } })
  })
  assert.equal((await completeSessionTask(session)).completed, true)
  assert.equal(writes, 0)
  mode = 'deleted'
  await assert.rejects(loadSessionTask(session), e => e.status === 404)
  await assert.rejects(completeSessionTask(session), e => e.status === 404)
  assert.equal(writes, 0)
  mode = 'failure'
  await assert.rejects(completeSessionTask(session), e => e.status === 500)
  assert.equal(writes, 1)
  clearCsrfTokenCache()
})
