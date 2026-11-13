import test from 'node:test'
import assert from 'node:assert/strict'
import { getTaskStudyTimes, formatStudyTime } from './taskStudyTime.js'

test('study time preserves seconds and does not invent remaining or rounded-up minutes', () => {
  assert.equal(formatStudyTime(0), '0 phút')
  assert.equal(formatStudyTime(1200), '20 phút')
  assert.equal(formatStudyTime(1215), '20 phút 15 giây')
  assert.equal(formatStudyTime(5), '5 giây')
})
test('task totals use bounded batches and do not lose tasks after the first 100', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push(url); assert.equal(options.credentials, 'same-origin')
    const ids = new URL(url, 'http://localhost').searchParams.get('taskIds').split(',')
    assert.ok(ids.length <= 100)
    return Response.json({ data: ids.map(taskId => ({ taskId, totalFocusSeconds: 1200, completedSessionCount: 1 })) })
  })
  const totals = await getTaskStudyTimes(Array.from({ length: 205 }, (_, i) => `task-${i}`))
  assert.equal(calls.length, 3); assert.equal(Object.keys(totals).length, 205)
  assert.equal(totals['task-204'].totalFocusSeconds, 1200)
})
test('unavailable or incomplete totals stay errors instead of claiming zero study time', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ error: {} }, { status: 401 }))
  await assert.rejects(getTaskStudyTimes(['a']), error => error.status === 401)
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: [] }))
  await assert.rejects(getTaskStudyTimes(['a']))
  assert.deepEqual(await getTaskStudyTimes([]), {})
})
