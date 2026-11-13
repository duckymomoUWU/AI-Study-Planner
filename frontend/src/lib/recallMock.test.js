import { test } from 'node:test'
import assert from 'node:assert/strict'
import { RecallApiError } from './recall.js'
import { createRecallMock } from './recallMock.js'

const KEY_A = '11111111-1111-4111-8111-111111111111'
const KEY_B = '22222222-2222-4222-8222-222222222222'
const KEY_C = '33333333-3333-4333-8333-333333333333'
const KEY_D = '44444444-4444-4444-8444-444444444444'
const mock = () => createRecallMock({ delayMs: 0 })

async function draftDeck(api, count = 5) {
  const material = await api.createTextMaterial({ goalId: 'g1', title: 'Nguồn', content: 'Nội dung ôn tập.' })
  const deck = await api.generateDeck({ materialId: material.id, title: 'Bộ thẻ', count }, KEY_A)
  return { material, deck, ...(await api.getDeck(deck.id)) }
}

test('the mock follows the contract: a new deck is a draft with cited cards', async () => {
  const api = mock()
  const { deck, cards } = await draftDeck(api)
  assert.equal(deck.status, 'DRAFT')
  assert.equal(cards.length, 5)
  assert.ok(cards.every(card => card.question && card.answer && card.source.quote && card.reviewCount === 0))
})

test('replaying the same generation key returns the same deck, a different payload is a conflict', async () => {
  const api = mock()
  const { material, deck } = await draftDeck(api)
  const again = await api.generateDeck({ materialId: material.id, title: 'Bộ thẻ', count: 5 }, KEY_A)
  assert.equal(again.id, deck.id)
  assert.equal((await api.listDecks({ goalId: 'g1' })).length, 1)
  await assert.rejects(api.generateDeck({ materialId: material.id, title: 'Khác', count: 5 }, KEY_A),
    error => error.status === 409 && error.code === 'IDEMPOTENCY_CONFLICT')
  const other = await api.generateDeck({ materialId: material.id, title: 'Bộ thẻ', count: 5 }, KEY_B)
  assert.notEqual(other.id, deck.id)
})

test('a failed source is not stored and the count must be 5 to 20', async () => {
  const api = mock()
  await assert.rejects(api.createTextMaterial({ goalId: 'g1', title: 'T', content: 'x'.repeat(30001) }),
    error => error.status === 422 && error.code === 'SOURCE_TOO_LONG')
  assert.equal((await api.listMaterials({ goalId: 'g1' })).length, 0)
  const material = await api.createTextMaterial({ goalId: 'g1', title: 'T', content: 'ok' })
  await assert.rejects(api.generateDeck({ materialId: material.id, title: 'T', count: 4 }, KEY_A), error => error.status === 400)
})

test('cards can be edited only in a draft, with the right version, and an edit bumps the deck version', async () => {
  const api = mock()
  const { deck, cards } = await draftDeck(api)
  const edited = await api.updateCard(cards[0].id, { question: 'Câu hỏi mới?' }, cards[0].version)
  assert.equal(edited.editedByUser, true)
  assert.equal(edited.version, 1)
  assert.equal((await api.getDeck(deck.id)).deck.version, 1)
  await assert.rejects(api.updateCard(cards[0].id, { answer: 'x' }, 0), error => error.code === 'CARD_VERSION_CONFLICT')
  await api.activateDeck(deck.id, 1)
  await assert.rejects(api.updateCard(cards[1].id, { answer: 'x' }, 0), error => error.code === 'DECK_STATE_CONFLICT')
})

test('activation needs the current deck version and reviews need an active deck', async () => {
  const api = mock()
  const { deck, cards } = await draftDeck(api)
  await assert.rejects(api.reviewCard(cards[0].id, 'REMEMBERED', KEY_C), error => error.code === 'DECK_STATE_CONFLICT')
  await assert.rejects(api.activateDeck(deck.id, 5), error => error.code === 'DECK_VERSION_CONFLICT')
  assert.equal((await api.activateDeck(deck.id, 0)).status, 'ACTIVE')
})

test('reviews are self-ratings; a retry with the same key writes once; progress uses the last rating', async () => {
  const api = mock()
  const { deck, cards } = await draftDeck(api)
  await api.activateDeck(deck.id, 0)
  await assert.rejects(api.reviewCard(cards[0].id, 'CORRECT', KEY_C), error => error.status === 400)

  const first = await api.reviewCard(cards[0].id, 'FORGOT', KEY_C)
  const retry = await api.reviewCard(cards[0].id, 'FORGOT', KEY_C)
  assert.equal(retry.reviewId, first.reviewId)
  assert.equal((await api.getDeck(deck.id)).cards[0].reviewCount, 1)

  const again = await api.reviewCard(cards[0].id, 'REMEMBERED', KEY_D)
  assert.deepEqual(again.progress, { totalCards: 5, reviewedCards: 1, rememberedCards: 1, forgottenCards: 0 })
  assert.equal((await api.getDeck(deck.id)).cards[0].reviewCount, 2)
  assert.deepEqual(await api.getProgress(deck.id), again.progress)
})

test('deleting a deck removes its cards and an unknown id is not found', async () => {
  const api = mock()
  const { deck, cards } = await draftDeck(api)
  await api.deleteDeck(deck.id)
  await assert.rejects(api.getDeck(deck.id), error => error instanceof RecallApiError && error.status === 404)
  await assert.rejects(api.updateCard(cards[0].id, { answer: 'x' }, 0), error => error.status === 404)
  assert.equal((await api.listDecks({})).length, 0)
})

test('a failed generation leaves the source, and retrying with the same key creates exactly one deck', async () => {
  const api = createRecallMock({ delayMs: 0, faults: ['generate'] })
  const material = await api.createTextMaterial({ goalId: 'g1', title: 'Nguồn', content: 'Nội dung.' })
  await assert.rejects(api.generateDeck({ materialId: material.id, title: 'Bộ', count: 5 }, KEY_A),
    error => error.status === 503 && error.code === 'AI_UNAVAILABLE')
  assert.equal((await api.listMaterials({ goalId: 'g1' })).length, 1)
  assert.equal((await api.listDecks({ goalId: 'g1' })).length, 0)
  const deck = await api.generateDeck({ materialId: material.id, title: 'Bộ', count: 5 }, KEY_A)
  const again = await api.generateDeck({ materialId: material.id, title: 'Bộ', count: 5 }, KEY_A)
  assert.equal(again.id, deck.id)
  assert.equal((await api.listDecks({ goalId: 'g1' })).length, 1)
})

test('a lost review request can be retried with the same key and is recorded once', async () => {
  const api = createRecallMock({ delayMs: 0, faults: ['review'] })
  const { deck, cards } = await draftDeck(api)
  await api.activateDeck(deck.id, 0)
  await assert.rejects(api.reviewCard(cards[0].id, 'FORGOT', KEY_C), error => error instanceof TypeError)
  assert.equal((await api.getDeck(deck.id)).cards[0].reviewCount, 0)
  await api.reviewCard(cards[0].id, 'FORGOT', KEY_C)
  await api.reviewCard(cards[0].id, 'FORGOT', KEY_C)
  assert.equal((await api.getDeck(deck.id)).cards[0].reviewCount, 1)
})

test('getMaterial returns the text the mock server read, capped at a preview, and 404 for an unknown source', async () => {
  const api = mock()
  const short = await api.createTextMaterial({ goalId: 'g1', title: 'Ngắn', content: 'Kiểm thử hộp đen.' })
  const detail = await api.getMaterial(short.id)
  assert.equal(detail.preview, 'Kiểm thử hộp đen.')
  assert.equal(detail.previewTruncated, false)
  assert.equal(detail.text, undefined, 'the list shape must not carry the whole text')
  const long = await api.createTextMaterial({ goalId: 'g1', title: 'Dài', content: 'x'.repeat(5000) })
  const longDetail = await api.getMaterial(long.id)
  assert.equal(longDetail.preview.length, 1200)
  assert.equal(longDetail.previewTruncated, true)
  assert.equal((await api.listMaterials({ goalId: 'g1' })).every(m => m.preview === undefined), true)
  await assert.rejects(api.getMaterial('nope'), error => error instanceof RecallApiError && error.status === 404)
})

