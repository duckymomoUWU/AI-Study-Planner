import { COUNT_MAX, COUNT_MIN, PDF_LIMIT_BYTES, RecallApiError, TEXT_LIMIT, deckProgress } from './recall.js'
import { deckFixture } from './recallFixtures.js'

/**
 * Development-only in-memory stand-in for /api/v1/recall, enabled with ?recallMock=1 in `npm run dev`.
 * It follows the agreed contract rules (draft-only edits, versions, activation, ratings, idempotent retries)
 * so the UI can be built before the real API is merged. The page shows a banner while it is on, and
 * it is never selected in a production build.
 */
export function createRecallMock({ delayMs = 120, faults = [] } = {}) {
  // One-shot failures for trying the error paths: 'generate' (AI unavailable) and 'review' (network lost).
  const pendingFaults = new Set(faults)
  let sequence = 0
  const materials = new Map()
  const texts = new Map() // what the 'server' read from each source
  const decks = new Map()
  const cards = new Map()
  const receipts = new Map()
  const nextId = prefix => `${prefix}-${String(++sequence).padStart(4, '0')}-4000-8000-${String(sequence).padStart(12, '0')}`
  const now = () => new Date().toISOString()
  const wait = () => new Promise(resolve => setTimeout(resolve, delayMs))
  const fail = (status, code, message, details = []) => new RecallApiError(message, { status, code, details })
  const notFound = () => fail(404, 'NOT_FOUND', 'Không tìm thấy dữ liệu.')

  function deckCards(deckId) {
    return [...cards.values()].filter(card => card.deckId === deckId).sort((a, b) => a.order - b.order)
  }
  const publicCard = card => {
    const copy = { ...card }
    delete copy.order
    return copy
  }
  function deckView(deck) {
    return { ...deck, cardCount: deckCards(deck.id).length }
  }
  function replay(key, fingerprint) {
    if (!key) throw fail(400, 'VALIDATION_ERROR', 'Thiếu Idempotency-Key.', [{ field: 'Idempotency-Key', issue: 'required' }])
    const stored = receipts.get(key)
    if (stored && stored.fingerprint !== fingerprint) throw fail(409, 'IDEMPOTENCY_CONFLICT', 'Khóa yêu cầu đã dùng cho thao tác khác.')
    return stored
  }

  async function createMaterial(body, kind) {
    await wait()
    const title = (body.title ?? '').trim()
    if (!title || title.length > 120) throw fail(400, 'VALIDATION_ERROR', 'Tên nguồn không hợp lệ.', [{ field: 'title', issue: 'invalid_length' }])
    if (kind === 'TEXT') {
      if (!body.content?.trim()) throw fail(400, 'VALIDATION_ERROR', 'Thiếu nội dung.', [{ field: 'content', issue: 'required' }])
      if (body.content.length > TEXT_LIMIT) throw fail(422, 'SOURCE_TOO_LONG', 'Nội dung quá dài.')
    } else if (!body.base64 || body.base64.length * 0.75 > PDF_LIMIT_BYTES) {
      throw fail(422, 'SOURCE_NO_TEXT', 'Không đọc được chữ trong tệp.')
    }
    const material = {
      id: nextId('66666666'), goalId: body.goalId, title, fileName: kind === 'PDF' ? body.fileName : null, kind,
      pageCount: kind === 'PDF' ? 1 : null, characterCount: kind === 'TEXT' ? body.content.length : 1200, createdAt: now(),
    }
    materials.set(material.id, material)
    texts.set(material.id, kind === 'TEXT' ? body.content : '(Dữ liệu mẫu) Chữ giả lập của tệp PDF.')
    return { ...material }
  }

  return {
    createTextMaterial: body => createMaterial(body, 'TEXT'),
    createPdfMaterial: body => createMaterial(body, 'PDF'),
    async getMaterial(materialId) {
      await wait()
      const material = materials.get(materialId)
      if (!material) throw notFound()
      const text = texts.get(materialId) ?? ''
      return { ...material, preview: text.slice(0, 1200), previewTruncated: text.length > 1200 }
    },
    async listMaterials({ goalId } = {}) {
      await wait()
      return [...materials.values()].filter(m => !goalId || m.goalId === goalId).map(m => ({ ...m }))
    },
    async generateDeck({ materialId, title, count }, key) {
      await wait()
      if (pendingFaults.delete('generate')) throw fail(503, 'AI_UNAVAILABLE', 'Dịch vụ AI tạm thời không dùng được.')
      const material = materials.get(materialId)
      if (!material) throw notFound()
      if (!Number.isInteger(count) || count < COUNT_MIN || count > COUNT_MAX) {
        throw fail(400, 'VALIDATION_ERROR', 'Số thẻ không hợp lệ.', [{ field: 'count', issue: 'out_of_range' }])
      }
      const fingerprint = `${materialId}|${title}|${count}`
      const stored = replay(key, fingerprint)
      if (stored) return { ...decks.get(stored.deckId) }
      const deck = {
        id: nextId('55555555'), goalId: material.goalId, materialId, title: (title || material.title).trim(),
        status: 'DRAFT', cardCount: 0, version: 0, createdAt: now(),
      }
      decks.set(deck.id, deck)
      deckFixture.cards.slice(0, Math.min(count, deckFixture.cards.length)).forEach((sample, order) => {
        const card = { ...sample, id: nextId('44444444'), deckId: deck.id, source: { ...sample.source, materialId }, order }
        cards.set(card.id, card)
      })
      receipts.set(key, { fingerprint, deckId: deck.id })
      return deckView(deck)
    },
    async listDecks({ goalId } = {}) {
      await wait()
      return [...decks.values()].filter(d => !goalId || d.goalId === goalId).map(deckView)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    },
    async getDeck(deckId) {
      await wait()
      const deck = decks.get(deckId)
      if (!deck) throw notFound()
      return { deck: deckView(deck), cards: deckCards(deckId).map(publicCard) }
    },
    async updateCard(cardId, { question, answer }, expectedVersion) {
      await wait()
      const card = cards.get(cardId)
      if (!card) throw notFound()
      const deck = decks.get(card.deckId)
      if (deck.status !== 'DRAFT') throw fail(409, 'DECK_STATE_CONFLICT', 'Chỉ sửa được bộ thẻ nháp.')
      if (card.version !== expectedVersion) throw fail(409, 'CARD_VERSION_CONFLICT', 'Thẻ đã thay đổi.')
      if (question !== undefined) card.question = question
      if (answer !== undefined) card.answer = answer
      card.editedByUser = true
      card.version += 1
      deck.version += 1
      return publicCard(card)
    },
    async activateDeck(deckId, expectedVersion) {
      await wait()
      const deck = decks.get(deckId)
      if (!deck) throw notFound()
      if (deck.version !== expectedVersion) throw fail(409, 'DECK_VERSION_CONFLICT', 'Bộ thẻ đã thay đổi.')
      if (deckCards(deckId).length === 0) throw fail(409, 'DECK_STATE_CONFLICT', 'Bộ thẻ rỗng.')
      deck.status = 'ACTIVE'
      deck.version += 1
      return deckView(deck)
    },
    async deleteDeck(deckId) {
      await wait()
      if (!decks.delete(deckId)) throw notFound()
      deckCards(deckId).forEach(card => cards.delete(card.id))
      return null
    },
    async reviewCard(cardId, rating, key) {
      await wait()
      if (pendingFaults.delete('review')) throw new TypeError('Failed to fetch')
      const card = cards.get(cardId)
      if (!card) throw notFound()
      if (rating !== 'REMEMBERED' && rating !== 'FORGOT') {
        throw fail(400, 'VALIDATION_ERROR', 'Đánh giá không hợp lệ.', [{ field: 'rating', issue: 'invalid_value' }])
      }
      const fingerprint = `${cardId}|${rating}`
      const stored = replay(key, fingerprint)
      if (stored) return stored.response
      if (decks.get(card.deckId).status !== 'ACTIVE') throw fail(409, 'DECK_STATE_CONFLICT', 'Hãy xác nhận bộ thẻ trước khi ôn.')
      card.lastRating = rating
      card.lastReviewedAt = now()
      card.reviewCount += 1
      const response = {
        reviewId: nextId('77777777'), cardId, rating, reviewedAt: card.lastReviewedAt,
        progress: deckProgress(deckCards(card.deckId)),
      }
      receipts.set(key, { fingerprint, response })
      return response
    },
    async getProgress(deckId) {
      await wait()
      if (!decks.has(deckId)) throw notFound()
      return deckProgress(deckCards(deckId))
    },
  }
}
