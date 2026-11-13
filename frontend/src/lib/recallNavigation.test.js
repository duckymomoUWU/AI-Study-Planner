import test from 'node:test'
import assert from 'node:assert/strict'
import { pageFromHash, recallParamsFromHash, taskFromHash } from './navigation.js'

test('pageFromHash parses routes correctly', () => {
  assert.equal(pageFromHash('#recall'), 'recall')
  assert.equal(pageFromHash('#recall?goal=123'), 'recall')
  assert.equal(pageFromHash('#daily'), 'daily')
  assert.equal(pageFromHash('#goals'), 'goals')
  assert.equal(pageFromHash(''), 'home')
})

test('recallParamsFromHash extracts goalId and deckId safely', () => {
  const goalId = '11111111-1111-1111-1111-111111111111'
  const deckId = '22222222-2222-2222-2222-222222222222'

  assert.deepEqual(recallParamsFromHash(''), { goalId: null, deckId: null })
  assert.deepEqual(recallParamsFromHash('#daily?goal=' + goalId), { goalId: null, deckId: null })

  assert.deepEqual(
    recallParamsFromHash(`#recall?goal=${goalId}&deck=${deckId}`),
    { goalId, deckId }
  )

  assert.deepEqual(
    recallParamsFromHash(`#recall?goal=${goalId}`),
    { goalId, deckId: null }
  )

  // Invalid non-UUID parameters are ignored safely
  assert.deepEqual(
    recallParamsFromHash('#recall?goal=not-a-uuid&deck=invalid'),
    { goalId: null, deckId: null }
  )
})

test('taskFromHash extracts timer taskId', () => {
  const taskId = '33333333-3333-3333-3333-333333333333'
  assert.equal(taskFromHash(`#timer?task=${taskId}`), taskId)
  assert.equal(taskFromHash('#timer?task=invalid'), null)
  assert.equal(taskFromHash('#daily?task=' + taskId), null)
})

