import test from 'node:test'
import assert from 'node:assert/strict'
import { remainingFocus, formatSeconds, getActiveSession, startStudySession, actOnStudySession, listStudySessions, sessionError } from './studySessions.js'
import { clearCsrfTokenCache } from './auth.js'
import { pageFromHash, taskFromHash } from './navigation.js'

test('focus countdown uses elapsed time, clamps at zero, and freezes paused sessions', () => {
  const running = { status: 'RUNNING', remainingSeconds: 1200 }
  assert.equal(remainingFocus(running, 1000, 61000), 1140)
  assert.equal(remainingFocus(running, 1000, 99999999), 0)
  assert.equal(remainingFocus({ ...running, status: 'PAUSED' }, 1000, 99999999), 1200)
  assert.equal(remainingFocus(running, 1000, 0), 1200)
  assert.equal(formatSeconds(1500), '25:00')
  assert.equal(formatSeconds(0), '00:00')
})

test('public home, explicit overview, registration and timer task links stay distinct', () => {
  assert.equal(pageFromHash(''), 'home')
  assert.equal(pageFromHash('#landing-steps'), 'landing')
  assert.equal(pageFromHash('#overview'), 'overview')
  assert.equal(pageFromHash('#register'), 'auth')
  const id = '22222222-2222-4222-8222-222222222222'
  assert.equal(pageFromHash(`#timer?task=${id}`), 'timer')
  assert.equal(taskFromHash(`#timer?task=${id}`), id)
  assert.equal(taskFromHash('#timer?task=fake'), null)
  assert.equal(taskFromHash(`#goals?task=${id}`), null)
})

test('session client uses real v1 endpoints, CSRF and stable retry payload/key', async t => {
  clearCsrfTokenCache()
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'test-token' } })
    if (url.endsWith('/active')) return Response.json({ data: { session: null } })
    if (url.includes('?page=')) return Response.json({ data: [] })
    return Response.json({ data: { id: 'session', status: 'RUNNING', remainingSeconds: 1400, focusedSeconds: 100, version: 0 } })
  })
  assert.equal(await getActiveSession(), null)
  await startStudySession('task-id', 'start-key')
  await actOnStudySession('session', 'FINISH', 3, 'finish-key')
  await actOnStudySession('session', 'FINISH', 3, 'finish-key')
  assert.deepEqual(await listStudySessions(), [])
  const writes = calls.filter(x => x.options.method === 'POST')
  assert.equal(writes[0].url, '/api/v1/study-sessions')
  assert.deepEqual(JSON.parse(writes[0].options.body), { taskId: 'task-id' })
  assert.equal(writes[0].options.credentials, 'same-origin')
  assert.equal(writes[0].options.headers['X-CSRF-TOKEN'], 'test-token')
  assert.deepEqual(writes[1], writes[2])
  assert.equal(writes[1].options.headers['Idempotency-Key'], 'finish-key')
  assert.deepEqual(JSON.parse(writes[1].options.body), { action: 'FINISH', expectedVersion: 3 })
  clearCsrfTokenCache()
})

test('missing backend and ambiguous write errors never become saved sessions', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ error: { code: 'NOT_FOUND' } }, { status: 404 }))
  await assert.rejects(getActiveSession(), error => error.status === 404 && sessionError(error).includes('Chưa có thời gian học nào được lưu'))
  assert.match(sessionError({ status: 409 }), /tab khác/)
  assert.match(sessionError({ status: 401 }), /đăng nhập/)
  assert.match(sessionError({}), /Chưa xác nhận/)
})

test('malformed successful responses cannot be mistaken for an idle or saved session', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: {} }))
  await assert.rejects(getActiveSession(), error => error.status === 502)
  await assert.rejects(listStudySessions(), error => error.status === 502)
})
