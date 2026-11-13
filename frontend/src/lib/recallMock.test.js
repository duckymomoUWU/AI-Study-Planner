import { test } from 'node:test'
import assert from 'node:assert/strict'
import { RecallApiError, describeRecallError } from './recall.js'
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

test('editing card to off-topic knowledge sets qualityWarning and prevents deck activation until resolved', async () => {
  const api = mock()
  const { deck, cards } = await draftDeck(api)

  // 1. Edit card into off-topic knowledge (Cristiano Ronaldo)
  const offTopic = await api.updateCard(cards[0].id, {
    question: 'Cristiano Ronaldo sinh năm bao nhiêu?',
    answer: '1985 tại Bồ Đào Nha'
  }, cards[0].version)

  assert.equal(offTopic.editedByUser, true)
  assert.ok(offTopic.qualityWarning, 'Expected qualityWarning to be set for off-topic edit')
  assert.ok(offTopic.qualityWarning.includes('không có căn cứ trong tài liệu nguồn'))

  // 2. Draft is saved without losing user's edits
  const deckAfterEdit = await api.getDeck(deck.id)
  assert.equal(deckAfterEdit.cards[0].question, 'Cristiano Ronaldo sinh năm bao nhiêu?')
  assert.equal(deckAfterEdit.cards[0].answer, '1985 tại Bồ Đào Nha')

  // 3. Activating deck with warned card is rejected
  await assert.rejects(
    api.activateDeck(deck.id, deckAfterEdit.deck.version),
    error => error.status === 400 && error.code === 'VALIDATION_ERROR' && error.details[0]?.issue === 'quality_issues'
  )

  // 4. Edit card back to valid grounded content
  const fixedCard = await api.updateCard(cards[0].id, {
    question: 'Khái niệm được học trong tài liệu là gì?',
    answer: 'Nội dung ôn tập chính xác.'
  }, offTopic.version)

  assert.equal(fixedCard.qualityWarning, null, 'Warning must be cleared on valid grounded edit')

  // 5. Deck activation now succeeds
  const updatedDeck = await api.getDeck(deck.id)
  const activated = await api.activateDeck(deck.id, updatedDeck.deck.version)
  assert.equal(activated.status, 'ACTIVE')
})

test('generateDeck accurately classifies all 6 error causes and formats Vietnamese messages', async () => {
  const api = mock()

  // 1. Material too short / single sentence (INSUFFICIENT_MATERIAL)
  const shortMaterial = await api.createTextMaterial({ goalId: 'g1', title: 'Ngắn', content: 'Hôm nay trời đẹp.' })
  try {
    await api.generateDeck({ materialId: shortMaterial.id, title: 'Bản nháp', count: 5 }, KEY_A)
    assert.fail('Expected INSUFFICIENT_MATERIAL error')
  } catch (err) {
    assert.equal(err.code, 'INSUFFICIENT_MATERIAL')
    const info = describeRecallError(err)
    assert.equal(info.message, 'Tài liệu chưa đủ nội dung để tạo câu hỏi. Vui lòng bổ sung thêm thông tin.')
  }

  // 2. Material with no suitable study content (NO_SUITABLE_CONTENT)
  const noStudyContent = await api.createTextMaterial({
    goalId: 'g1', title: 'Chào hỏi',
    content: 'Xin chào các bạn. Chúc một ngày tốt lành và vui vẻ. Hẹn gặp lại nhé. Nội dung này không phù hợp để tạo thẻ.'
  })
  try {
    await api.generateDeck({ materialId: noStudyContent.id, title: 'Bản nháp', count: 5 }, KEY_A)
    assert.fail('Expected NO_SUITABLE_CONTENT error')
  } catch (err) {
    assert.equal(err.code, 'NO_SUITABLE_CONTENT')
    const info = describeRecallError(err)
    assert.equal(info.message, 'Không tìm thấy nội dung phù hợp để tạo flashcard.')
  }

  // 3. AI Timeout (AI_TIMEOUT)
  const validMaterial = await api.createTextMaterial({
    goalId: 'g1', title: 'Tài liệu chuẩn',
    content: 'Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ gồm các thành phần lưu trữ và thực thi truy vấn. Khóa chính định danh duy nhất.'
  })
  const timeoutApi = createRecallMock({ delayMs: 0, faults: ['timeout'] })
  const m1 = await timeoutApi.createTextMaterial({ goalId: 'g1', title: 'T', content: validMaterial.content })
  try {
    await timeoutApi.generateDeck({ materialId: m1.id, title: 'Bản nháp', count: 5 }, KEY_A)
    assert.fail('Expected AI_TIMEOUT error')
  } catch (err) {
    assert.equal(err.code, 'AI_TIMEOUT')
    const info = describeRecallError(err)
    assert.equal(info.message, 'AI hiện phản hồi quá lâu. Vui lòng thử lại sau.')
  }

  // 4. AI Malformed Response (AI_MALFORMED_RESPONSE)
  const malformedApi = createRecallMock({ delayMs: 0, faults: ['malformed'] })
  const m2 = await malformedApi.createTextMaterial({ goalId: 'g1', title: 'T', content: validMaterial.content })
  try {
    await malformedApi.generateDeck({ materialId: m2.id, title: 'Bản nháp', count: 5 }, KEY_A)
    assert.fail('Expected AI_MALFORMED_RESPONSE error')
  } catch (err) {
    assert.equal(err.code, 'AI_MALFORMED_RESPONSE')
    const info = describeRecallError(err)
    assert.equal(info.message, 'Không thể xử lý kết quả tạo thẻ. Vui lòng thử lại.')
  }

  // 5. Connection Error / Unavailable / Gateway Error (GATEWAY_ERROR / AI_UNAVAILABLE)
  const gatewayApi = createRecallMock({ delayMs: 0, faults: ['gateway'] })
  const m3 = await gatewayApi.createTextMaterial({ goalId: 'g1', title: 'T', content: validMaterial.content })
  try {
    await gatewayApi.generateDeck({ materialId: m3.id, title: 'Bản nháp', count: 5 }, KEY_A)
    assert.fail('Expected GATEWAY_ERROR')
  } catch (err) {
    assert.equal(err.code, 'GATEWAY_ERROR')
    const info = describeRecallError(err)
    assert.equal(info.message, 'Không thể kết nối đến dịch vụ tạo flashcard. Vui lòng thử lại sau.')
  }

  // 6. All generated cards failed quality check (ALL_CARDS_FAILED_QUALITY)
  const lowQualityMat = await api.createTextMaterial({
    goalId: 'g1', title: 'Kém chất lượng',
    content: 'Tài liệu kiến thức có nội dung kém chất lượng khiến tất cả thẻ tạo ra đều bị lỗi trích dẫn.'
  })
  try {
    await api.generateDeck({ materialId: lowQualityMat.id, title: 'Bản nháp', count: 5 }, KEY_A)
    assert.fail('Expected ALL_CARDS_FAILED_QUALITY error')
  } catch (err) {
    assert.equal(err.code, 'ALL_CARDS_FAILED_QUALITY')
    const info = describeRecallError(err)
    assert.equal(info.message, 'Các thẻ được tạo chưa đáp ứng tiêu chí chất lượng. Vui lòng bổ sung hoặc chỉnh sửa tài liệu.')
  }

  // 7. Partial quality failure: preserves valid cards and marks the flawed one
  const partialMat = await api.createTextMaterial({
    goalId: 'g1', title: 'Tài liệu',
    content: 'Tài liệu kiến thức chuẩn nhưng có cảnh báo một thẻ để người dùng xem xét và chỉnh sửa.'
  })
  const deck = await api.generateDeck({ materialId: partialMat.id, title: 'Bản nháp', count: 5 }, KEY_A)
  const detail = await api.getDeck(deck.id)
  assert.equal(detail.cards.length, 5)
  const warned = detail.cards.filter(c => c.qualityWarning)
  assert.equal(warned.length, 1)
  assert.equal(warned[0].qualityWarning, 'Đoạn trích dẫn cần đối chiếu lại ngữ nghĩa.')
})
