import { test } from 'node:test'
import assert from 'node:assert/strict'
import { describeTaskError } from './taskErrors.js'
import { TaskApiError } from './tasks.js'

test('401 asks the user to log in and does not reload', () => {
  const r = describeTaskError(new TaskApiError('x', { status: 401, code: 'UNAUTHORIZED' }))
  assert.equal(r.unauthenticated, true)
  assert.equal(r.reload, false)
})

test('404 reloads the list because the task is gone or not the user\'s', () => {
  const r = describeTaskError(new TaskApiError('x', { status: 404, code: 'NOT_FOUND' }))
  assert.equal(r.reload, true)
  assert.match(r.message, /Không tìm thấy công việc/)
})

test('CSRF failure asks for a page reload; other errors keep the server message', () => {
  assert.match(describeTaskError(new TaskApiError('x', { status: 403, code: 'CSRF_INVALID' })).message, /tải lại trang/)
  assert.equal(describeTaskError(new TaskApiError('Lỗi máy chủ', { status: 500 })).message, 'Lỗi máy chủ')
  assert.equal(describeTaskError(new Error('boom')).message, 'boom')
})

test('405 says the feature is not available on the server yet', () => {
  const r = describeTaskError(new TaskApiError('x', { status: 405, code: 'METHOD_NOT_ALLOWED' }))
  assert.match(r.message, /chưa khả dụng/)
  assert.equal(r.reload, false)
})
