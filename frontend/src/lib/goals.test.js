import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createGoal, deleteGoal, GoalApiError, listGoals, updateGoal, parseSyllabusDocument } from './goals.js'

test("listGoals requests the current user list and validates response shape", async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/goals')
    assert.equal(options.method, 'GET')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers.Accept, 'application/json')
    return Response.json({ items: [{ id: 'goal-1', subject: 'Toán' }] })
  })
  assert.deepEqual(await listGoals(), [{ id: 'goal-1', subject: 'Toán' }])
})

test('createGoal sends JSON with same-origin credentials', async t => {
  const input = { subject: 'Toán', topics: 'Tích phân', startDate: '2026-11-09', examDate: '2026-12-05', minutesPerDay: 90 }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/goals')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(options.body), input)
    assert.equal(options.credentials, 'same-origin')
    return Response.json({ id: 'goal-1', ...input }, { status: 201 })
  })
  assert.equal((await createGoal(input)).id, 'goal-1')
})

test('updateGoal encodes the goal ID and includes optimistic version', async t => {
  const input = { subject: 'Toán', topics: 'Tích phân', startDate: '2026-11-09', examDate: '2026-12-05', minutesPerDay: 90, version: 3 }
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/goals/goal%2F1')
    assert.equal(options.method, 'PUT')
    assert.equal(JSON.parse(options.body).version, 3)
    return Response.json({ id: 'goal/1', ...input })
  })
  assert.equal((await updateGoal('goal/1', input)).version, 3)
})

test('deleteGoal sends DELETE and accepts 204', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/goals/goal-1')
    assert.equal(options.method, 'DELETE')
    return new Response(null, { status: 204 })
  })
  assert.equal(await deleteGoal('goal-1'), null)
})

test('API errors preserve status, code and field-level validation details', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({
    code: 'validation_failed',
    message: 'Dữ liệu mục tiêu không hợp lệ.',
    fieldErrors: [{ field: 'examDate', code: 'past_date', message: 'Ngày thi đã qua.' }],
  }, { status: 400 }))
  await assert.rejects(createGoal({}), error => {
    assert.ok(error instanceof GoalApiError)
    assert.equal(error.status, 400)
    assert.equal(error.code, 'validation_failed')
    assert.equal(error.fieldErrors[0].field, 'examDate')
    return true
  })
})

test('listGoals exposes 401 instead of treating unauthenticated access as an empty list', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({
    code: 'unauthenticated', message: 'Bạn cần đăng nhập.',
  }, { status: 401 }))
  await assert.rejects(listGoals(), error => error.status === 401 && error.code === 'unauthenticated')
})

test('parseSyllabusDocument posts to /api/goals/parse-syllabus and returns extracted fields', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/goals/parse-syllabus')
    assert.equal(options.method, 'POST')
    const body = JSON.parse(options.body)
    assert.equal(body.fileName, 'de_cuong_csdl.txt')
    return Response.json({
      data: {
        subject: 'Cơ sở dữ liệu',
        topics: 'Mô hình ER, SQL JOIN, Chuẩn hóa 3NF',
        startDate: '2026-11-10',
        examDate: '2026-12-10',
        minutesPerDay: 90,
      },
    })
  })

  const result = await parseSyllabusDocument({
    content: 'Môn: Cơ sở dữ liệu',
    fileName: 'de_cuong_csdl.txt',
  })
  assert.equal(result.subject, 'Cơ sở dữ liệu')
  assert.equal(result.minutesPerDay, 90)
})
