import { getCsrfToken } from './auth.js'

const BASE = '/api/v1/study-sessions'
export class SessionApiError extends Error {
  constructor(message, { status, code, context } = {}) {
    super(message); this.name = 'SessionApiError'; this.status = status; this.code = code; this.context = context
  }
}
async function request(path, { method = 'GET', body, key, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') {
    headers['X-CSRF-TOKEN'] = await getCsrfToken()
    headers['Content-Type'] = 'application/json'
    headers['Idempotency-Key'] = key
  }
  const r = await fetch(path, { method, headers, credentials: 'same-origin', cache: 'no-store', signal, body: body === undefined ? undefined : JSON.stringify(body) })
  const payload = await r.json().catch(() => null)
  if (!r.ok) throw new SessionApiError(payload?.error?.message || 'Không thể kết nối phiên học.', { status: r.status, code: payload?.error?.code, context: payload?.error?.context })
  if (!payload || !Object.hasOwn(payload, 'data')) throw new SessionApiError('Phản hồi phiên học không hợp lệ.', { status: 502 })
  return payload
}
function session(value) {
  if (!value || typeof value.id !== 'string' || !['RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED'].includes(value.status)
    || !Number.isInteger(value.remainingSeconds) || value.remainingSeconds < 0 || value.remainingSeconds > 1500
    || !Number.isInteger(value.focusedSeconds) || value.focusedSeconds < 0 || value.focusedSeconds > 1500
    || !Number.isInteger(value.version) || value.version < 0) {
    throw new SessionApiError('Phản hồi phiên học không hợp lệ.', { status: 502 })
  }
  return value
}
export async function getActiveSession({ signal } = {}) {
  const data = (await request(`${BASE}/active`, { signal })).data
  if (data?.session === null) return null
  return session(data?.session)
}
export async function getStudySession(id, { signal } = {}) { return session((await request(`${BASE}/${encodeURIComponent(id)}`, { signal })).data) }
export async function startStudySession(taskId, key, { signal } = {}) { return session((await request(BASE, { method: 'POST', body: { taskId }, key, signal })).data) }
export async function actOnStudySession(id, action, version, key, { signal } = {}) { return session((await request(`${BASE}/${encodeURIComponent(id)}/actions`, { method: 'POST', body: { action, expectedVersion: version }, key, signal })).data) }
export async function listStudySessions({ signal } = {}) {
  const rows = (await request(`${BASE}?page=1&limit=10`, { signal })).data
  if (!Array.isArray(rows)) throw new SessionApiError('Không đọc được lịch sử phiên học.', { status: 502 })
  return rows.map(session)
}
export function remainingFocus(snapshot, receivedAt, now) {
  if (!snapshot) return 1500
  const elapsed = snapshot.status === 'RUNNING' ? Math.floor(Math.max(0, now - receivedAt) / 1000) : 0
  return Math.max(0, snapshot.remainingSeconds - elapsed)
}
export function formatSeconds(seconds) {
  const s = Math.max(0, Math.floor(seconds))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}
export function sessionError(error) {
  if (error.status === 401) return 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại để tải phiên học.'
  if (error.status === 404) return 'Chưa kết nối được dịch vụ phiên học. Chưa có thời gian học nào được lưu.'
  if (error.status === 409) return 'Phiên học đã thay đổi ở tab khác. Hãy tải lại trạng thái trước khi tiếp tục.'
  return 'Chưa xác nhận được kết quả từ máy chủ. Bạn có thể thử lại cùng yêu cầu; không bắt đầu một phiên mới.'
}
