import { test } from 'node:test'
import assert from 'node:assert/strict'
import { describeGoalError } from './goalErrors.js'
import { GoalApiError } from './goals.js'

const fe = (field, code, message) => ({ field, code, message })

test('validation errors keep every message of each field', () => {
  const error = new GoalApiError('Dữ liệu mục tiêu không hợp lệ.', {
    status: 400,
    code: 'validation_failed',
    fieldErrors: [fe('examDate', 'past_date', 'A'), fe('examDate', 'over_90_days', 'B'), fe('subject', 'required', 'C')],
  })
  const result = describeGoalError(error)
  assert.deepEqual(result.fieldErrors, { examDate: ['A', 'B'], subject: ['C'] })
  assert.equal(result.message, 'Dữ liệu mục tiêu không hợp lệ.')
  assert.deepEqual(result.conflicts, [])
})

test('goal_tasks_conflict shows the server message and the list of blocking tasks/days', () => {
  const error = new GoalApiError('Thay đổi làm công việc hiện có vi phạm.', {
    status: 409,
    code: 'goal_tasks_conflict',
    fieldErrors: [fe('examDate', 'task_after_deadline', 'Công việc "X" ngày 2026-12-01 nằm sau ngày thi'),
      fe('minutesPerDay', 'daily_budget_exceeded', 'Ngày 2026-11-20 có 120 phút, vượt 20 phút')],
  })
  const result = describeGoalError(error)
  assert.equal(result.message, 'Thay đổi làm công việc hiện có vi phạm.')
  assert.equal(result.conflicts.length, 2)
  assert.match(result.conflicts[1], /2026-11-20/)
  assert.ok(!/nơi khác/.test(result.message))
})

test('stale version 409 asks the user to reload', () => {
  const result = describeGoalError(new GoalApiError('x', { status: 409, code: 'stale_goal_version' }))
  assert.match(result.message, /tải lại/)
  assert.deepEqual(result.conflicts, [])
})

test('401, 404 and unknown errors map to clear messages', () => {
  assert.match(describeGoalError(new GoalApiError('x', { status: 401 })).message, /đăng nhập/)
  const notFound = describeGoalError(new GoalApiError('x', { status: 404 })).message
  assert.match(notFound, /Không tìm thấy mục tiêu/)
  assert.ok(!/Backend/.test(notFound))
  assert.equal(describeGoalError(new Error('boom')).message, 'boom')
})
