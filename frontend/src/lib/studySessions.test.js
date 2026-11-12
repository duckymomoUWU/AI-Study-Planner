import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  actOnSession,
  calculateRemainingSeconds,
  formatTime,
  getActiveSession,
  getGoalStudyTime,
  listStudySessions,
  SessionApiError,
  startSession,
} from './studySessions.js'

const sampleSession = (overrides = {}) => ({
  id: '11111111-1111-4111-8111-111111111111',
  taskId: '22222222-2222-4222-8222-222222222222',
  goalId: '33333333-3333-4333-8333-333333333333',
  taskTitle: 'Ôn kiểm thử hộp đen',
  subject: 'Kiểm tra phần mềm',
  status: 'RUNNING',
  plannedFocusSeconds: 1500,
  focusedSeconds: 120,
  remainingSeconds: 1380,
  focusComplete: false,
  startedAt: '2026-11-11T02:00:00Z',
  lastResumedAt: '2026-11-11T02:00:00Z',
  endedAt: null,
  serverNow: '2026-11-11T02:02:00Z',
  version: 0,
  ...overrides,
})

test('formatTime formats seconds to mm:ss correctly', () => {
  assert.equal(formatTime(0), '00:00')
  assert.equal(formatTime(5), '00:05')
  assert.equal(formatTime(65), '01:05')
  assert.equal(formatTime(1500), '25:00')
  assert.equal(formatTime(-10), '00:00')
  assert.equal(formatTime('invalid'), '00:00')
})

test('calculateRemainingSeconds handles RUNNING, PAUSED, and focusComplete accurately', () => {
  const baseTime = 1000000000000
  const sessionRunning = sampleSession({ status: 'RUNNING', remainingSeconds: 100 })

  // 10 seconds later
  const remainingRunning = calculateRemainingSeconds(sessionRunning, baseTime + 10000, baseTime)
  assert.equal(remainingRunning, 90)

  // Paused session should not decrease with elapsed time
  const sessionPaused = sampleSession({ status: 'PAUSED', remainingSeconds: 100 })
  const remainingPaused = calculateRemainingSeconds(sessionPaused, baseTime + 10000, baseTime)
  assert.equal(remainingPaused, 100)

  // Complete session always 0
  const sessionComplete = sampleSession({ status: 'RUNNING', remainingSeconds: 10, focusComplete: true })
  assert.equal(calculateRemainingSeconds(sessionComplete, baseTime, baseTime), 0)

  // Past remainingSeconds clamped to 0
  assert.equal(calculateRemainingSeconds(sessionRunning, baseTime + 120000, baseTime), 0)
})

test('getActiveSession unwraps active session and returns null when no active session', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    assert.equal(url, '/api/v1/study-sessions/active')
    return Response.json({ data: { session: sampleSession() } })
  })

  const session = await getActiveSession()
  assert.equal(session.id, '11111111-1111-4111-8111-111111111111')
  assert.equal(session.status, 'RUNNING')

  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: { session: null } }))
  const noSession = await getActiveSession()
  assert.equal(noSession, null)

  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: {} }))
  await assert.rejects(getActiveSession(), error => error instanceof SessionApiError && error.status === 502)
})

test('startSession requires taskId, sends POST with CSRF and Idempotency-Key', async t => {
  await assert.rejects(startSession(null), error => error.status === 400 && error.code === 'VALIDATION_ERROR')

  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'mock-csrf' } })
    }
    assert.equal(url, '/api/v1/study-sessions')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'mock-csrf')
    assert.equal(options.headers['Idempotency-Key'], 'custom-key-123')
    const body = JSON.parse(options.body)
    assert.equal(body.taskId, 'task-xyz')
    return Response.json({ data: sampleSession({ taskId: 'task-xyz' }) })
  })

  const created = await startSession('task-xyz', 'custom-key-123')
  assert.equal(created.taskId, 'task-xyz')
})

test('actOnSession sends action and expectedVersion with Idempotency-Key', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'mock-csrf' } })
    }
    assert.equal(url, '/api/v1/study-sessions/11111111-1111-4111-8111-111111111111/actions')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['Idempotency-Key'], 'act-key-456')
    const body = JSON.parse(options.body)
    assert.equal(body.action, 'PAUSE')
    assert.equal(body.expectedVersion, 0)
    return Response.json({ data: sampleSession({ status: 'PAUSED', version: 1 }) })
  })

  const updated = await actOnSession('11111111-1111-4111-8111-111111111111', 'PAUSE', 0, 'act-key-456')
  assert.equal(updated.status, 'PAUSED')
  assert.equal(updated.version, 1)
})

test('API errors preserve HTTP status, code, details and context', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    if (url === '/api/v1/auth/csrf') {
      return Response.json({ data: { token: 'mock-csrf' } })
    }
    return new Response(
      JSON.stringify({
        error: {
          code: 'SESSION_VERSION_CONFLICT',
          message: 'Phiên học đã thay đổi ở nơi khác.',
          details: [{ field: 'expectedVersion', issue: 'stale_version' }],
          context: { currentVersion: 2 },
        },
      }),
      { status: 409, headers: { 'Content-Type': 'application/json' } }
    )
  })

  await assert.rejects(
    actOnSession('session-1', 'PAUSE', 0, 'key-1'),
    error =>
      error instanceof SessionApiError &&
      error.status === 409 &&
      error.code === 'SESSION_VERSION_CONFLICT' &&
      error.context?.currentVersion === 2 &&
      error.details?.[0]?.field === 'expectedVersion'
  )
})

test('listStudySessions and getGoalStudyTime query correct endpoints', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    if (url.includes('/study-sessions?')) {
      assert.ok(url.includes('taskId=task-1'))
      assert.ok(url.includes('page=1'))
      assert.ok(url.includes('limit=20'))
      return Response.json({ data: [sampleSession()], meta: { page: 1, limit: 20, totalItems: 1, totalPages: 1 } })
    }
    if (url === '/api/v1/goals/goal-1/study-time') {
      return Response.json({ data: { completedSessionCount: 3, totalFocusSeconds: 4500 } })
    }
    throw new Error('Unexpected URL: ' + url)
  })

  const list = await listStudySessions({ taskId: 'task-1', page: 1, limit: 20 })
  assert.equal(list.length, 1)

  const stats = await getGoalStudyTime('goal-1')
  assert.equal(stats.completedSessionCount, 3)
  assert.equal(stats.totalFocusSeconds, 4500)
})

