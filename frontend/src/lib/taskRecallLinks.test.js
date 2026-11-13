import test from 'node:test'
import assert from 'node:assert/strict'
import {
  _resetMemoryStoreForTesting,
  getTaskRecallLink,
  setTaskRecallLink,
  removeTaskRecallLink,
  validateAndSyncTaskRecallLink,
} from './taskRecallLinks.js'

test('taskRecallLinks: save and retrieve a link', () => {
  _resetMemoryStoreForTesting()
  const taskId = 't-1'
  const deck = {
    deckId: 'd-100',
    deckTitle: 'Flashcard Cơ sở dữ liệu',
    cardCount: 15,
    status: 'ACTIVE',
    goalId: 'g-1',
  }

  assert.equal(getTaskRecallLink(taskId), null)

  const saved = setTaskRecallLink(taskId, deck)
  assert.equal(saved.taskId, taskId)
  assert.equal(saved.deckId, 'd-100')
  assert.equal(saved.deckTitle, 'Flashcard Cơ sở dữ liệu')
  assert.equal(saved.cardCount, 15)
  assert.equal(saved.status, 'ACTIVE')
  assert.equal(saved.goalId, 'g-1')

  const retrieved = getTaskRecallLink(taskId)
  assert.deepEqual(retrieved, saved)
})

test('taskRecallLinks: remove a link', () => {
  _resetMemoryStoreForTesting()
  const taskId = 't-2'
  setTaskRecallLink(taskId, {
    deckId: 'd-200',
    deckTitle: 'Hệ điều hành',
    cardCount: 8,
    status: 'DRAFT',
    goalId: 'g-2',
  })

  assert.ok(getTaskRecallLink(taskId))
  removeTaskRecallLink(taskId)
  assert.equal(getTaskRecallLink(taskId), null)
})

test('taskRecallLinks: validateAndSync removes link if goalId changed', () => {
  _resetMemoryStoreForTesting()
  const taskId = 't-3'
  setTaskRecallLink(taskId, {
    deckId: 'd-300',
    deckTitle: 'Mạng máy tính',
    cardCount: 10,
    status: 'ACTIVE',
    goalId: 'g-original',
  })

  // Goal changed to g-new
  const result = validateAndSyncTaskRecallLink(taskId, 'g-new')
  assert.equal(result, null)
  assert.equal(getTaskRecallLink(taskId), null, 'Link should have been removed because goal changed')
})

test('taskRecallLinks: validateAndSync removes link if deck is not in availableDecks', () => {
  _resetMemoryStoreForTesting()
  const taskId = 't-4'
  setTaskRecallLink(taskId, {
    deckId: 'd-400',
    deckTitle: 'Toán rời rạc',
    cardCount: 5,
    status: 'ACTIVE',
    goalId: 'g-math',
  })

  const availableDecks = [
    { id: 'd-other', title: 'Toán cao cấp', cardCount: 12, status: 'ACTIVE', goalId: 'g-math' },
  ]

  const result = validateAndSyncTaskRecallLink(taskId, 'g-math', availableDecks)
  assert.equal(result, null)
  assert.equal(getTaskRecallLink(taskId), null, 'Link should be removed because deck d-400 was deleted')
})

test('taskRecallLinks: validateAndSync updates deck metadata when valid', () => {
  _resetMemoryStoreForTesting()
  const taskId = 't-5'
  setTaskRecallLink(taskId, {
    deckId: 'd-500',
    deckTitle: 'Kiến trúc máy tính (Cũ)',
    cardCount: 5,
    status: 'DRAFT',
    goalId: 'g-arch',
  })

  const availableDecks = [
    { id: 'd-500', title: 'Kiến trúc máy tính (Mới)', cardCount: 20, status: 'ACTIVE', goalId: 'g-arch' },
  ]

  const result = validateAndSyncTaskRecallLink(taskId, 'g-arch', availableDecks)
  assert.ok(result)
  assert.equal(result.deckTitle, 'Kiến trúc máy tính (Mới)')
  assert.equal(result.cardCount, 20)
  assert.equal(result.status, 'ACTIVE')
})

