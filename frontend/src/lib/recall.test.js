import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  PDF_LIMIT_BYTES, RecallApiError, TEXT_LIMIT, activateDeck, clampCount, createPdfMaterial, createTextMaterial,
  deckProgress, deleteDeck, describeRecallError, firstUnreviewedIndex, generateDeck, getDeck, getProgress,
  isStudyComplete, listDecks, listMaterials, newKey, reviewCard, updateCard, validateCardText, validatePdfFile,
  validateTextSource,
} from './recall.js'
import { deckFixture, materialFixture } from './recallFixtures.js'

const KEY = '44444444-4444-4444-8444-444444444444'
const csrf = () => Response.json({ data: { token: 'csrf-1', headerName: 'X-CSRF-TOKEN' } })

/** Mocks fetch: the CSRF endpoint is answered, every other call goes to `handler`. */
function mockApi(t, handler) {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url === '/api/v1/auth/csrf') return csrf()
    calls.push({ url, options })
    return handler(url, options)
  })
  return calls
}

const rated = (rating, index) => ({ ...deckFixture.cards[index], lastRating: rating })

test('createTextMaterial posts goalId, title and content with CSRF and returns the stored material', async t => {
  const calls = mockApi(t, () => Response.json({ data: { id: 'm1', kind: 'TEXT' } }, { status: 201 }))
  const material = await createTextMaterial({ goalId: materialFixture.goalId, title: 'T', content: materialFixture.content })
  assert.equal(material.id, 'm1')
  const { url, options } = calls[0]
  assert.equal(url, '/api/v1/recall/materials')
  assert.equal(options.method, 'POST')
  assert.equal(options.credentials, 'same-origin')
  assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-1')
  assert.deepEqual(JSON.parse(options.body), { goalId: materialFixture.goalId, title: 'T', content: materialFixture.content })
})

test('createPdfMaterial sends one source kind only: base64 with the PDF mime type, no text', async t => {
  const calls = mockApi(t, () => Response.json({ data: { id: 'm2', kind: 'PDF' } }, { status: 201 }))
  await createPdfMaterial({ goalId: 'g', title: 'T', fileName: 'a.pdf', base64: 'QUJD' })
  const body = JSON.parse(calls[0].options.body)
  assert.deepEqual(body, { goalId: 'g', title: 'T', fileName: 'a.pdf', mimeType: 'application/pdf', base64: 'QUJD' })
  assert.equal(body.content, undefined)
})

test('listMaterials and listDecks send paging and the optional goalId', async t => {
  const calls = mockApi(t, () => Response.json({ data: [], meta: { page: 1, limit: 100, totalItems: 0, totalPages: 0 } }))
  await listMaterials({ goalId: 'g 1' })
  await listDecks({})
  assert.equal(calls[0].url, '/api/v1/recall/materials?page=1&limit=100&goalId=g+1')
  assert.equal(calls[1].url, '/api/v1/recall/decks?page=1&limit=100')
  assert.equal(calls[0].options.method, 'GET')
  assert.equal(calls[0].options.headers['X-CSRF-TOKEN'], undefined)
})

test('listMaterials and listDecks follow every page so items past the first 100 are not dropped', async t => {
  const item = id => ({ id, title: id })
  const calls = mockApi(t, url => {
    const page = Number(new URL(url, 'http://x').searchParams.get('page'))
    const data = page === 1 ? Array.from({ length: 100 }, (_, i) => item(`a${i}`)) : page === 2 ? Array.from({ length: 30 }, (_, i) => item(`b${i}`)) : []
    return Response.json({ data, meta: { page, limit: 100, totalItems: 130, totalPages: 2 } })
  })
  const materials = await listMaterials({ goalId: 'g1' })
  assert.equal(materials.length, 130)
  assert.equal(new Set(materials.map(m => m.id)).size, 130)
  assert.deepEqual(calls.map(c => c.url), [
    '/api/v1/recall/materials?page=1&limit=100&goalId=g1',
    '/api/v1/recall/materials?page=2&limit=100&goalId=g1',
  ])
  assert.equal((await listDecks({ goalId: 'g1' })).length, 130)
})

test('a list response without a data array is a malformed response, not an empty list', async t => {
  mockApi(t, () => Response.json({ items: [], meta: { totalPages: 1 } }))
  await assert.rejects(listMaterials({}), error => error.status === 502 && error.code === 'INVALID_RESPONSE')
})

test('generateDeck sends the Idempotency-Key and the count, and keeps it identical on a retry', async t => {
  const calls = mockApi(t, () => Response.json({ data: { id: 'd1', status: 'DRAFT' } }, { status: 201 }))
  await generateDeck({ materialId: 'm1', title: 'Bộ thẻ', count: 8 }, KEY)
  await generateDeck({ materialId: 'm1', title: 'Bộ thẻ', count: 8 }, KEY)
  for (const { url, options } of calls) {
    assert.equal(url, '/api/v1/recall/decks')
    assert.equal(options.headers['Idempotency-Key'], KEY)
    assert.deepEqual(JSON.parse(options.body), { materialId: 'm1', title: 'Bộ thẻ', count: 8 })
  }
})

test('getDeck returns deck and cards and rejects a malformed response', async t => {
  mockApi(t, () => Response.json({ data: deckFixture }))
  const loaded = await getDeck('d/1')
  assert.equal(loaded.cards.length, 5)
  assert.equal(loaded.deck.status, 'DRAFT')
  t.mock.restoreAll()
  mockApi(t, () => Response.json({ data: { deck: { id: 'x' } } }))
  await assert.rejects(getDeck('x'), error => error.status === 502 && error.code === 'INVALID_RESPONSE')
  t.mock.restoreAll()
  mockApi(t, () => Response.json({ items: [] }))
  await assert.rejects(getDeck('x'), error => error.status === 502)
})

test('updateCard patches only question, answer and expectedVersion on the encoded card path', async t => {
  const calls = mockApi(t, () => Response.json({ data: { ...deckFixture.cards[0], version: 1, editedByUser: true } }))
  const card = await updateCard('c/1', { question: 'Q?', answer: 'A.' }, 0)
  assert.equal(card.editedByUser, true)
  assert.equal(calls[0].url, '/api/v1/recall/cards/c%2F1')
  assert.equal(calls[0].options.method, 'PATCH')
  assert.deepEqual(JSON.parse(calls[0].options.body), { expectedVersion: 0, question: 'Q?', answer: 'A.' })
})

test('activateDeck posts expectedVersion; deleteDeck accepts 204', async t => {
  const calls = mockApi(t, (url, options) => options.method === 'DELETE'
    ? new Response(null, { status: 204 })
    : Response.json({ data: { id: 'd1', status: 'ACTIVE', version: 2 } }))
  assert.equal((await activateDeck('d1', 1)).status, 'ACTIVE')
  assert.deepEqual(JSON.parse(calls[0].options.body), { expectedVersion: 1 })
  assert.equal(calls[0].url, '/api/v1/recall/decks/d1/activate')
  assert.equal(await deleteDeck('d1'), null)
  assert.equal(calls[1].options.method, 'DELETE')
})

test('reviewCard sends only the rating with a key and returns the progress from the server', async t => {
  const progress = { totalCards: 5, reviewedCards: 1, rememberedCards: 1, forgottenCards: 0 }
  const calls = mockApi(t, () => Response.json({ data: { reviewId: 'r1', cardId: 'c1', rating: 'REMEMBERED', progress } }))
  const result = await reviewCard('c1', 'REMEMBERED', KEY)
  assert.deepEqual(result.progress, progress)
  assert.deepEqual(JSON.parse(calls[0].options.body), { rating: 'REMEMBERED' })
  assert.equal(calls[0].options.headers['Idempotency-Key'], KEY)
  assert.equal(calls[0].url, '/api/v1/recall/cards/c1/reviews')
})

test('getProgress reads the four counters', async t => {
  mockApi(t, () => Response.json({ data: { totalCards: 5, reviewedCards: 2, rememberedCards: 1, forgottenCards: 1 } }))
  assert.equal((await getProgress('d1')).reviewedCards, 2)
})

test('API errors keep status, code, details and context from the error envelope', async t => {
  mockApi(t, () => Response.json({ error: {
    code: 'DECK_VERSION_CONFLICT', message: 'Bộ thẻ đã đổi.',
    details: [{ field: 'expectedVersion', issue: 'stale_version' }], context: { currentVersion: 3 },
  } }, { status: 409 }))
  await assert.rejects(activateDeck('d1', 0), error => {
    assert.ok(error instanceof RecallApiError)
    assert.equal(error.status, 409)
    assert.equal(error.code, 'DECK_VERSION_CONFLICT')
    assert.equal(error.details[0].issue, 'stale_version')
    assert.equal(error.context.currentVersion, 3)
    return true
  })
})

test('a missing route is surfaced as an error and never replaced by sample cards', async t => {
  mockApi(t, () => Response.json({ error: { code: 'NOT_FOUND', message: 'x' } }, { status: 404 }))
  await assert.rejects(listDecks({}), error => error.status === 404)
  await assert.rejects(generateDeck({ materialId: 'm', title: 'T', count: 10 }, KEY), error => error.status === 404)
  await assert.rejects(reviewCard('c', 'FORGOT', KEY), error => error.status === 404)
})

test('newKey returns a fresh UUID each time', () => {
  const keys = new Set(Array.from({ length: 20 }, newKey))
  assert.equal(keys.size, 20)
  for (const key of keys) assert.match(key, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
})

test('deckProgress counts the last rating of each card and never exceeds the total', () => {
  const cards = [rated('REMEMBERED', 0), rated('FORGOT', 1), { ...deckFixture.cards[2], lastRating: null }]
  assert.deepEqual(deckProgress(cards), { totalCards: 3, reviewedCards: 2, rememberedCards: 1, forgottenCards: 1 })
  assert.deepEqual(deckProgress([]), { totalCards: 0, reviewedCards: 0, rememberedCards: 0, forgottenCards: 0 })
  const all = deckFixture.cards.map(card => ({ ...card, lastRating: 'REMEMBERED', reviewCount: 4 }))
  assert.ok(deckProgress(all).reviewedCards <= deckProgress(all).totalCards)
})

test('study resumes at the first unreviewed card and completes only when every card has a rating', () => {
  const cards = deckFixture.cards.map(card => ({ ...card }))
  assert.equal(firstUnreviewedIndex(cards), 0)
  cards[0].lastRating = 'REMEMBERED'
  cards[1].lastRating = 'FORGOT'
  assert.equal(firstUnreviewedIndex(cards), 2)
  assert.equal(isStudyComplete(cards), false)
  cards.forEach(card => { card.lastRating = 'REMEMBERED' })
  assert.equal(isStudyComplete(cards), true)
  assert.equal(firstUnreviewedIndex(cards), 0)
  assert.equal(isStudyComplete([]), false)
})

test('validateTextSource checks title and content limits with Vietnamese messages', () => {
  assert.deepEqual(validateTextSource({ title: 'Chương 1', content: 'Nội dung' }), {})
  assert.ok(validateTextSource({ title: '  ', content: 'x' }).title)
  assert.ok(validateTextSource({ title: 'a'.repeat(121), content: 'x' }).title)
  assert.ok(validateTextSource({ title: 'A', content: '   ' }).content)
  assert.equal(validateTextSource({ title: 'A', content: 'x'.repeat(TEXT_LIMIT) }).content, undefined)
  assert.match(validateTextSource({ title: 'A', content: 'x'.repeat(TEXT_LIMIT + 1) }).content, /tối đa/)
})

test('validatePdfFile accepts a PDF up to 5 MiB and rejects other files', () => {
  const pdf = (name, size, type = 'application/pdf') => ({ name, size, type })
  assert.deepEqual(validatePdfFile(pdf('a.pdf', 1000), 'T'), {})
  assert.deepEqual(validatePdfFile(pdf('A.PDF', PDF_LIMIT_BYTES), 'T'), {})
  assert.ok(validatePdfFile(pdf('a.pdf', PDF_LIMIT_BYTES + 1), 'T').file)
  assert.ok(validatePdfFile(pdf('a.png', 100, 'image/png'), 'T').file)
  assert.ok(validatePdfFile(pdf('a.pdf', 100, 'text/plain'), 'T').file)
  assert.ok(validatePdfFile(pdf('a.pdf', 0), 'T').file)
  assert.ok(validatePdfFile(null, 'T').file)
  assert.ok(validatePdfFile(pdf('a.pdf', 10), '').title)
})

test('validateCardText limits question and answer and requires both', () => {
  assert.deepEqual(validateCardText({ question: 'Q?', answer: 'A' }), {})
  assert.ok(validateCardText({ question: ' ', answer: 'A' }).question)
  assert.ok(validateCardText({ question: 'q'.repeat(301), answer: 'A' }).question)
  assert.ok(validateCardText({ question: 'Q', answer: '' }).answer)
  assert.ok(validateCardText({ question: 'Q', answer: 'a'.repeat(2001) }).answer)
})

test('clampCount keeps the card count within 5 to 20 and defaults to 10', () => {
  assert.equal(clampCount(10), 10)
  assert.equal(clampCount(2), 5)
  assert.equal(clampCount(99), 20)
  assert.equal(clampCount('12'), 12)
  assert.equal(clampCount('abc'), 10)
  assert.equal(clampCount(7.9), 7)
})

test('describeRecallError decides by status and code, not by message text', () => {
  const err = (status, code) => new RecallApiError('máy chủ nói gì đó', { status, code })
  assert.equal(describeRecallError(err(401, 'UNAUTHORIZED')).kind, 'unauthenticated')
  assert.equal(describeRecallError(err(403, 'CSRF_INVALID')).kind, 'reload')
  assert.equal(describeRecallError(err(404, 'NOT_FOUND')).kind, 'reload')
  assert.equal(describeRecallError(err(409, 'DECK_VERSION_CONFLICT')).kind, 'reload')
  assert.equal(describeRecallError(err(409, 'CARD_VERSION_CONFLICT')).kind, 'reload')
  assert.equal(describeRecallError(err(409, 'DECK_STATE_CONFLICT')).kind, 'reload')
  assert.equal(describeRecallError(err(409, 'GENERATION_IN_PROGRESS')).kind, 'retry')
  assert.equal(describeRecallError(err(422, 'SOURCE_NO_TEXT')).kind, 'input')
  assert.equal(describeRecallError(err(422, 'SOURCE_TOO_LONG')).kind, 'input')
  const malformed = describeRecallError(err(502, 'AI_MALFORMED_RESPONSE'))
  assert.equal(malformed.kind, 'retry')
  assert.equal(malformed.message, 'Không thể xử lý kết quả tạo thẻ. Vui lòng thử lại.')
  assert.equal(describeRecallError(err(503, 'AI_UNAVAILABLE')).message, 'Không thể kết nối đến dịch vụ tạo flashcard. Vui lòng thử lại sau.')
  assert.equal(describeRecallError(err(422, 'INSUFFICIENT_MATERIAL')).message, 'Tài liệu chưa đủ nội dung để tạo câu hỏi. Vui lòng bổ sung thêm thông tin.')
  assert.equal(describeRecallError(err(422, 'NO_SUITABLE_CONTENT')).message, 'Không tìm thấy nội dung phù hợp để tạo flashcard.')
  assert.equal(describeRecallError(err(422, 'ALL_CARDS_FAILED_QUALITY')).message, 'Các thẻ được tạo chưa đáp ứng tiêu chí chất lượng. Vui lòng bổ sung hoặc chỉnh sửa tài liệu.')
  assert.equal(describeRecallError(err(504, 'AI_TIMEOUT')).kind, 'retry')
  assert.equal(describeRecallError(err(504, 'AI_TIMEOUT')).message, 'AI hiện phản hồi quá lâu. Vui lòng thử lại sau.')
  assert.equal(describeRecallError(new TypeError('Failed to fetch')).kind, 'retry')
  assert.match(describeRecallError(new TypeError('x')).message, /chưa được lưu/)
})
