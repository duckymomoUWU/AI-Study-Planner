import { getCsrfToken } from './auth.js'

// Active Recall client for the /api/v1/recall contract (docs/team-handoff/03-API-CONTRACT.md, section C).
// Every call goes to the real API; the only substitute is the explicit development mock in recallMock.js.

const BASE = '/api/v1/recall'
const PAGE_LIMIT = 100 // the API's maximum page size
const MAX_PAGES = 100 // a safety stop: 10,000 items per list is far beyond the product's scope

export const TEXT_LIMIT = 30000
export const TITLE_LIMIT = 120
export const PDF_LIMIT_BYTES = 5 * 1024 * 1024
export const COUNT_MIN = 5
export const COUNT_MAX = 20
export const COUNT_DEFAULT = 10
export const QUESTION_LIMIT = 300
export const ANSWER_LIMIT = 2000

export class RecallApiError extends Error {
  constructor(message, { status, code, details = [], context = {} } = {}) {
    super(message)
    this.name = 'RecallApiError'
    this.status = status
    this.code = code
    this.details = Array.isArray(details) ? details : []
    this.context = context && typeof context === 'object' ? context : {}
  }
}

/** A fresh Idempotency-Key. Keep it for retries of the same attempt; take a new one for a different action. */
export function newKey() {
  return globalThis.crypto.randomUUID()
}

async function request(path, { method = 'GET', body, key, signal } = {}) {
  const headers = { Accept: 'application/json' }
  const options = { method, headers, signal, credentials: 'same-origin' }
  if (method !== 'GET') {
    const token = await getCsrfToken().catch(() => null)
    if (token) headers['X-CSRF-TOKEN'] = token
  }
  if (key) headers['Idempotency-Key'] = key
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  const response = await fetch(`${BASE}${path}`, options)
  if (response.status === 204) return { data: null }
  let payload = null
  const text = await response.text()
  if (text) {
    try { payload = JSON.parse(text) } catch { payload = null }
  }
  if (!response.ok) {
    const error = payload?.error
    throw new RecallApiError(error?.message || `Không thể thực hiện yêu cầu (HTTP ${response.status}).`, {
      status: response.status, code: error?.code, details: error?.details, context: error?.context,
    })
  }
  if (!payload || !Object.hasOwn(payload, 'data')) {
    throw new RecallApiError('Phản hồi thẻ ôn tập không đúng định dạng.', { status: 502, code: 'INVALID_RESPONSE' })
  }
  return payload
}

function malformed() {
  return new RecallApiError('Phản hồi thẻ ôn tập không đúng định dạng.', { status: 502, code: 'INVALID_RESPONSE' })
}

/** POST /recall/materials with pasted text. */
export async function createTextMaterial({ goalId, title, content }, { signal } = {}) {
  const { data } = await request('/materials', { method: 'POST', body: { goalId, title, content }, signal })
  if (!data?.id) throw malformed()
  return data
}

/** POST /recall/materials with a text PDF encoded as base64. */
export async function createPdfMaterial({ goalId, title, fileName, base64 }, { signal } = {}) {
  const { data } = await request('/materials', {
    method: 'POST', body: { goalId, title, fileName, mimeType: 'application/pdf', base64 }, signal,
  })
  if (!data?.id) throw malformed()
  return data
}

/** Every item of a paged list: follows meta.totalPages so nothing past the first 100 is silently dropped. */
async function listAll(path, { goalId } = {}, { signal } = {}) {
  const items = []
  for (let page = 1; page <= MAX_PAGES; page += 1) {
    const query = new URLSearchParams({ page: String(page), limit: String(PAGE_LIMIT) })
    if (goalId) query.set('goalId', goalId)
    const { data, meta } = await request(`${path}?${query}`, { signal })
    if (!Array.isArray(data)) throw malformed()
    items.push(...data)
    if (page >= (typeof meta?.totalPages === 'number' ? meta.totalPages : 1)) break
  }
  return items
}

export function listMaterials(filter = {}, callOptions = {}) {
  return listAll('/materials', filter, callOptions)
}

/** POST /recall/decks. The key makes a retry of the same attempt return the same deck. */
export async function generateDeck({ materialId, title, count = COUNT_DEFAULT }, key, { signal } = {}) {
  const { data } = await request('/decks', { method: 'POST', body: { materialId, title, count }, key, signal })
  if (!data?.id) throw malformed()
  return data
}

export function listDecks(filter = {}, callOptions = {}) {
  return listAll('/decks', filter, callOptions)
}

/** GET /recall/decks/{id}: the deck with all of its cards. */
export async function getDeck(deckId, { signal } = {}) {
  const { data } = await request(`/decks/${encodeURIComponent(deckId)}`, { signal })
  if (!data?.deck?.id || !Array.isArray(data.cards)) throw malformed()
  return data
}

/** PATCH /recall/cards/{id}: only question and answer, only while the deck is a draft. */
export async function updateCard(cardId, { question, answer }, expectedVersion, { signal } = {}) {
  const body = { expectedVersion }
  if (question !== undefined) body.question = question
  if (answer !== undefined) body.answer = answer
  const { data } = await request(`/cards/${encodeURIComponent(cardId)}`, { method: 'PATCH', body, signal })
  if (!data?.id) throw malformed()
  return data
}

export async function activateDeck(deckId, expectedVersion, { signal } = {}) {
  const { data } = await request(`/decks/${encodeURIComponent(deckId)}/activate`, {
    method: 'POST', body: { expectedVersion }, signal,
  })
  if (!data?.id) throw malformed()
  return data
}

export async function deleteDeck(deckId, { signal } = {}) {
  await request(`/decks/${encodeURIComponent(deckId)}`, { method: 'DELETE', signal })
  return null
}

/** POST /recall/cards/{id}/reviews. A self-assessment: REMEMBERED or FORGOT, never graded by AI. */
export async function reviewCard(cardId, rating, key, { signal } = {}) {
  const { data } = await request(`/cards/${encodeURIComponent(cardId)}/reviews`, {
    method: 'POST', body: { rating }, key, signal,
  })
  if (!data?.cardId || !data.progress) throw malformed()
  return data
}

export async function getProgress(deckId, { signal } = {}) {
  const { data } = await request(`/decks/${encodeURIComponent(deckId)}/progress`, { signal })
  if (typeof data?.totalCards !== 'number') throw malformed()
  return data
}

// ---- Pure helpers (no network) ----

/** Progress from the last rating of each card; unreviewed cards are not counted as forgotten. */
export function deckProgress(cards) {
  const remembered = cards.filter(card => card.lastRating === 'REMEMBERED').length
  const forgotten = cards.filter(card => card.lastRating === 'FORGOT').length
  return {
    totalCards: cards.length,
    reviewedCards: remembered + forgotten,
    rememberedCards: remembered,
    forgottenCards: forgotten,
  }
}

/** Index of the first card without a rating, so a reload resumes where the student stopped; 0 when all are rated. */
export function firstUnreviewedIndex(cards) {
  const index = cards.findIndex(card => !card.lastRating)
  return index === -1 ? 0 : index
}

export function isStudyComplete(cards) {
  return cards.length > 0 && cards.every(card => card.lastRating)
}

/** Field errors for a pasted text source; an empty object means the input can be sent. */
export function validateTextSource({ title, content }) {
  const errors = {}
  const cleanTitle = (title ?? '').trim()
  if (!cleanTitle) errors.title = 'Hãy đặt tên cho nguồn tài liệu.'
  else if (cleanTitle.length > TITLE_LIMIT) errors.title = `Tên nguồn tối đa ${TITLE_LIMIT} ký tự.`
  const text = content ?? ''
  if (!text.trim()) errors.content = 'Hãy dán nội dung cần ôn tập.'
  else if (text.length > TEXT_LIMIT) {
    errors.content = `Nội dung dài ${text.length.toLocaleString('vi-VN')} ký tự, tối đa ${TEXT_LIMIT.toLocaleString('vi-VN')}. Hãy chọn đoạn ngắn hơn.`
  }
  return errors
}

/** Field errors for a chosen PDF file: it must be a PDF of at most 5 MiB. Page count and text are checked by the server. */
export function validatePdfFile(file, title) {
  const errors = {}
  const cleanTitle = (title ?? '').trim()
  if (!cleanTitle) errors.title = 'Hãy đặt tên cho nguồn tài liệu.'
  else if (cleanTitle.length > TITLE_LIMIT) errors.title = `Tên nguồn tối đa ${TITLE_LIMIT} ký tự.`
  if (!file) errors.file = 'Hãy chọn một tệp PDF.'
  else if (!/\.pdf$/i.test(file.name) || (file.type && file.type !== 'application/pdf')) errors.file = 'Chỉ nhận tệp PDF.'
  else if (file.size === 0) errors.file = 'Tệp PDF đang trống.'
  else if (file.size > PDF_LIMIT_BYTES) errors.file = 'Tệp PDF lớn hơn 5 MiB. Hãy chọn tệp nhỏ hơn.'
  return errors
}

export function validateCardText({ question, answer }) {
  const errors = {}
  const q = (question ?? '').trim()
  const a = (answer ?? '').trim()
  if (!q) errors.question = 'Câu hỏi không được để trống.'
  else if (q.length > QUESTION_LIMIT) errors.question = `Câu hỏi tối đa ${QUESTION_LIMIT} ký tự.`
  if (!a) errors.answer = 'Đáp án không được để trống.'
  else if (a.length > ANSWER_LIMIT) errors.answer = `Đáp án tối đa ${ANSWER_LIMIT} ký tự.`
  return errors
}

export function clampCount(value) {
  const number = Math.trunc(Number(value))
  if (!Number.isFinite(number)) return COUNT_DEFAULT
  return Math.min(COUNT_MAX, Math.max(COUNT_MIN, number))
}

/** File to base64 without the data: prefix, for the PDF source body. */
export function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('Không đọc được tệp. Hãy thử chọn lại.'))
    reader.onload = () => {
      const result = String(reader.result)
      resolve(result.slice(result.indexOf(',') + 1))
    }
    reader.readAsDataURL(file)
  })
}

/**
 * What to tell the student and what to do next, decided by status and code, never by the message text.
 * kind: unauthenticated | reload (stale data, reload the deck) | retry (same key may be resent) | input (fix the input).
 */
export function describeRecallError(error) {
  if (!(error instanceof RecallApiError)) {
    return { kind: 'retry', message: 'Không kết nối được máy chủ. Dữ liệu chưa được lưu, hãy thử lại.' }
  }
  const { status, code } = error
  if (status === 401) return { kind: 'unauthenticated', message: 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.' }
  if (status === 403 && code === 'CSRF_INVALID') {
    return { kind: 'reload', message: 'Phiên làm việc đã hết hạn. Hãy tải lại trang rồi thử lại.' }
  }
  if (status === 404) return { kind: 'reload', message: 'Không tìm thấy dữ liệu. Có thể đã bị xóa; danh sách sẽ được tải lại.' }
  if (status === 400) return { kind: 'input', message: error.message || 'Dữ liệu chưa hợp lệ.' }
  if (code === 'DECK_VERSION_CONFLICT' || code === 'CARD_VERSION_CONFLICT') {
    return { kind: 'reload', message: 'Bộ thẻ đã thay đổi ở nơi khác. Hãy xem bản mới nhất rồi làm lại.' }
  }
  if (code === 'DECK_STATE_CONFLICT' || code === 'MATERIAL_CHANGED') {
    return { kind: 'reload', message: 'Trạng thái bộ thẻ đã đổi. Hãy xem lại nguồn hoặc bộ thẻ.' }
  }
  if (code === 'GENERATION_IN_PROGRESS') {
    return { kind: 'retry', message: 'Bộ thẻ đang được tạo. Hãy chờ vài giây rồi bấm thử lại.' }
  }
  if (code === 'IDEMPOTENCY_CONFLICT') {
    return { kind: 'reload', message: 'Yêu cầu này đã được dùng cho thao tác khác. Hãy tải lại rồi thử lại.' }
  }
  if (code === 'SOURCE_NO_TEXT') {
    return { kind: 'input', message: 'Không đọc được chữ trong tệp. Hãy chọn PDF có văn bản hoặc dán nội dung vào.' }
  }
  if (code === 'SOURCE_TOO_LONG') {
    return { kind: 'input', message: 'Nội dung quá dài. Hãy chọn đoạn ngắn hơn hoặc dán phần cần ôn.' }
  }
  // Flashcard Generation Specific Errors
  if (code === 'INSUFFICIENT_MATERIAL') {
    return { kind: 'input', message: 'Tài liệu chưa đủ nội dung để tạo câu hỏi. Vui lòng bổ sung thêm thông tin.' }
  }
  if (code === 'NO_SUITABLE_CONTENT') {
    return { kind: 'input', message: 'Không tìm thấy nội dung phù hợp để tạo flashcard.' }
  }
  if (code === 'AI_MALFORMED_RESPONSE' || code === 'AI_INVALID_RESPONSE') {
    return { kind: 'retry', message: 'Không thể xử lý kết quả tạo thẻ. Vui lòng thử lại.' }
  }
  if (code === 'ALL_CARDS_FAILED_QUALITY') {
    return { kind: 'input', message: 'Các thẻ được tạo chưa đáp ứng tiêu chí chất lượng. Vui lòng bổ sung hoặc chỉnh sửa tài liệu.' }
  }
  if (code === 'AI_TIMEOUT' || status === 504) {
    return { kind: 'retry', message: 'AI hiện phản hồi quá lâu. Vui lòng thử lại sau.' }
  }
  if (code === 'AI_UNAVAILABLE' || code === 'GATEWAY_ERROR' || status === 502 || status === 503) {
    return { kind: 'retry', message: 'Không thể kết nối đến dịch vụ tạo flashcard. Vui lòng thử lại sau.' }
  }
  if (status === 500) {
    return { kind: 'retry', message: 'Không thể kết nối đến dịch vụ tạo flashcard. Vui lòng thử lại sau.' }
  }
  if (status === 422) return { kind: 'input', message: error.message || 'Nội dung chưa dùng được.' }
  if (status === 405 || status === 404) {
    return { kind: 'retry', message: 'Chức năng này chưa khả dụng trên máy chủ.' }
  }
  return { kind: 'retry', message: error.message || 'Đã xảy ra lỗi. Vui lòng thử lại.' }
}
