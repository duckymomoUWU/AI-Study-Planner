import { test } from 'node:test'
import assert from 'node:assert/strict'
import { describeGoalError } from './goalErrors.js'
import { GoalApiError } from './goals.js'

const fe = (field, issue, message) => ({ field, issue, message })

test('validation errors keep every message of each field', () => {
  const error = new GoalApiError('Dữ liệu mục tiêu không hợp lệ.', {
    status: 400,
    code: 'VALIDATION_ERROR',
    fieldErrors: [fe('examDate', 'must_not_be_in_past', 'A'), fe('examDate', 'exceeds_90_days', 'B'), fe('subject', 'required', 'C')],
  })
  const result = describeGoalError(error)
  assert.deepEqual(result.fieldErrors, { examDate: ['A', 'B'], subject: ['C'] })
  assert.equal(result.message, 'Dữ liệu mục tiêu không hợp lệ.')
  assert.deepEqual(result.conflicts, [])
})

test('GOAL_UPDATE_CONFLICT shows the server message and the list of blocking tasks/days', () => {
  const error = new GoalApiError('Thay đổi làm công việc hiện có vi phạm.', {
    status: 409,
    code: 'GOAL_UPDATE_CONFLICT',
    fieldErrors: [fe('examDate', 'task_on_or_after_exam_date', 'Công việc "X" ngày 2026-12-01 nằm sau ngày thi'),
      fe('minutesPerDay', 'daily_budget_exceeded', 'Ngày 2026-11-20 có 120 phút, vượt 20 phút')],
  })
  const result = describeGoalError(error)
  assert.equal(result.message, 'Thay đổi làm công việc hiện có vi phạm.')
  assert.equal(result.conflicts.length, 2)
  assert.match(result.conflicts[1], /2026-11-20/)
  assert.ok(!/nơi khác/.test(result.message))
})

test('stale version 409 asks the user to reload', () => {
  const result = describeGoalError(new GoalApiError('x', { status: 409, code: 'GOAL_VERSION_CONFLICT' }))
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
