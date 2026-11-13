import { test } from 'node:test'
import assert from 'node:assert/strict'
import { confirmAndDeleteGoal } from './goalDelete.js'

const goal = { id: 'goal-1', subject: 'Toán' }

test('cancelling the confirmation sends no delete request', async () => {
  let called = 0
  const result = await confirmAndDeleteGoal(goal, { confirm: () => false, remove: async () => { called += 1 } })
  assert.equal(result, 'cancelled')
  assert.equal(called, 0)
})

test('confirming deletes exactly that goal and warns about related data', async () => {
  const removed = []
  let prompt = ''
  const result = await confirmAndDeleteGoal(goal, {
    confirm: message => { prompt = message; return true },
    remove: async id => { removed.push(id) },
  })
  assert.equal(result, 'deleted')
  assert.deepEqual(removed, ['goal-1'])
  assert.match(prompt, /Toán/)
  assert.match(prompt, /công việc và bản nháp/)
})

test('a server failure propagates so the UI cannot show success', async () => {
  await assert.rejects(
    confirmAndDeleteGoal(goal, { confirm: () => true, remove: async () => { throw new Error('404') } }),
    /404/,
  )
})
