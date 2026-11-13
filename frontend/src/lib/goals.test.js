import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createGoal, deleteGoal, GoalApiError, getGoal, listGoals, updateGoal } from './goals.js'

const goal = (id, extra = {}) => ({
  id, subject: 'Toán', topics: 'Tích phân', startDate: '2026-11-09', examDate: '2026-12-05',
  minutesPerDay: 90, timezone: 'Asia/Ho_Chi_Minh', version: 0, createdAt: '2026-11-07T03:00:00Z', ...extra,
})
const meta = (page, totalPages, totalItems = 1) => ({ page, limit: 100, totalItems, totalPages })

test('listGoals requests /api/v1/goals with paging and unwraps data', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals?page=1&limit=100')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers.Accept, 'application/json')
    return Response.json({ data: [goal('goal-1')], meta: meta(1, 1) })
  })
  assert.deepEqual((await listGoals()).map(g => g.id), ['goal-1'])
})

test('listGoals follows every page and rejects a malformed response', async t => {
  const seen = []
  t.mock.method(globalThis, 'fetch', async url => {
    seen.push(url)
    return url.includes('page=1')
      ? Response.json({ data: [goal('a')], meta: meta(1, 2, 2) })
      : Response.json({ data: [goal('b')], meta: meta(2, 2, 2) })
  })
  assert.deepEqual((await listGoals()).map(g => g.id), ['a', 'b'])
  assert.equal(seen.length, 2)

  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => Response.json({ items: [] }))
  await assert.rejects(listGoals(), error => error.status === 502)
})

test('createGoal posts the five fields to /api/v1/goals and returns data', async t => {
  const input = { subject: 'Toán', topics: 'Tích phân', startDate: '2026-11-09', examDate: '2026-12-05', minutesPerDay: 90 }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(options.body), input)
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ data: goal('goal-1') }, { status: 201 })
  })
  assert.equal((await createGoal(input)).id, 'goal-1')
})

test('updateGoal PATCHes the encoded ID with the changes and expectedVersion', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals/goal%2F1')
    assert.equal(options.method, 'PATCH')
    assert.deepEqual(JSON.parse(options.body), { minutesPerDay: 60, expectedVersion: 3 })
    return Response.json({ data: goal('goal/1', { version: 4, minutesPerDay: 60 }) })
  })
  assert.equal((await updateGoal('goal/1', { minutesPerDay: 60 }, 3)).version, 4)
})

test('getGoal requests the detail and rejects malformed responses', async t => {
  t.mock.method(globalThis, 'fetch', async url => {
    assert.equal(url, '/api/v1/goals/goal%2F1')
    return Response.json({ data: goal('goal/1') })
  })
  assert.equal((await getGoal('goal/1')).subject, 'Toán')
  t.mock.restoreAll()
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: { nope: true } }))
  await assert.rejects(getGoal('x'), error => error.status === 502)
})

test('deleteGoal sends DELETE and accepts 204', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/v1/goals/goal-1')
    assert.equal(options.method, 'DELETE')
    return new Response(null, { status: 204 })
  })
  assert.equal(await deleteGoal('goal-1'), null)
})

test('API errors keep status, code and field details from the error envelope', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({
    error: {
      code: 'VALIDATION_ERROR',
      message: 'Dữ liệu mục tiêu không hợp lệ.',
      details: [{ field: 'examDate', issue: 'must_not_be_in_past', message: 'Ngày thi đã qua.' }],
    },
  }, { status: 400 }))
  await assert.rejects(createGoal({}), error => {
    assert.ok(error instanceof GoalApiError)
    assert.equal(error.status, 400)
    assert.equal(error.code, 'VALIDATION_ERROR')
    assert.equal(error.fieldErrors[0].field, 'examDate')
    assert.equal(error.fieldErrors[0].issue, 'must_not_be_in_past')
    return true
  })
})

test('listGoals exposes 401 instead of treating unauthenticated access as an empty list', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({
    error: { code: 'UNAUTHORIZED', message: 'Bạn cần đăng nhập.' },
  }, { status: 401 }))
  await assert.rejects(listGoals(), error => error.status === 401 && error.code === 'UNAUTHORIZED')
})

test('404 is surfaced as an error and never replaced with demo data', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ error: { code: 'NOT_FOUND', message: 'x' } }, { status: 404 }))
  await assert.rejects(listGoals(), error => error.status === 404)
  await assert.rejects(createGoal({ subject: 'A' }), error => error.status === 404)
  await assert.rejects(updateGoal('goal-1', { subject: 'A' }, 0), error => error.status === 404)
  await assert.rejects(deleteGoal('goal-1'), error => error.status === 404)
})
