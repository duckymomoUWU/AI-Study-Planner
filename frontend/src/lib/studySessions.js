import { getCsrfToken } from './auth.js'

export class SessionApiError extends Error {
  constructor(message, status, code, details = [], context = {}) {
    super(message)
    this.name = 'SessionApiError'
    this.status = status
    this.code = code
    this.details = Array.isArray(details) ? details : []
    this.context = context && typeof context === 'object' ? context : {}
  }
}

export function generateIdempotencyKey() {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export function formatTime(totalSeconds) {
  const safeSeconds = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const minutes = Math.floor(safeSeconds / 60)
  const seconds = safeSeconds % 60
  const pad = n => String(n).padStart(2, '0')
  return `${pad(minutes)}:${pad(seconds)}`
}

export function calculateRemainingSeconds(session, now = Date.now(), receivedTime = now) {
  if (!session) return 0
  if (session.focusComplete || (session.remainingSeconds !== undefined && session.remainingSeconds <= 0)) {
    return 0
  }
  const baseRemaining = Number(session.remainingSeconds) || 0
  if (session.status !== 'RUNNING') {
    return Math.max(0, baseRemaining)
  }
  const elapsedSeconds = Math.max(0, Math.floor((now - receivedTime) / 1000))
  return Math.max(0, baseRemaining - elapsedSeconds)
}

async function request(path, { method = 'GET', body, key, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') {
    headers['X-CSRF-TOKEN'] = await getCsrfToken()
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (key) {
    headers['Idempotency-Key'] = key
  }

  const response = await fetch(`/api/v1${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    signal,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })

  if (response.status === 204) {
    return null
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new SessionApiError(
      payload?.error?.message || 'Không thể thực hiện yêu cầu phiên học.',
      response.status,
      payload?.error?.code || 'UNKNOWN_ERROR',
      payload?.error?.details || [],
      payload?.error?.context || {}
    )
  }

  if (!payload || !Object.hasOwn(payload, 'data')) {
    throw new SessionApiError('Phản hồi phiên học chưa hợp lệ.', 502, 'INVALID_RESPONSE')
  }

  return payload.data
}

export async function getActiveSession(options = {}) {
  const data = await request('/study-sessions/active', options)
  if (!data || !Object.hasOwn(data, 'session')) {
    throw new SessionApiError('Thiếu trạng thái phiên học.', 502, 'INVALID_RESPONSE')
  }
  return data.session
}

export async function startSession(taskId, key, options = {}) {
  if (!taskId) {
    throw new SessionApiError('Cần chọn công việc trước khi bắt đầu phiên học.', 400, 'VALIDATION_ERROR')
  }
  const idempotencyKey = key || generateIdempotencyKey()
  return request('/study-sessions', {
    method: 'POST',
    body: { taskId },
    key: idempotencyKey,
    ...options,
  })
}

export async function getSession(id, options = {}) {
  return request(`/study-sessions/${encodeURIComponent(id)}`, options)
}

export async function actOnSession(id, action, expectedVersion, key, options = {}) {
  const idempotencyKey = key || generateIdempotencyKey()
  return request(`/study-sessions/${encodeURIComponent(id)}/actions`, {
    method: 'POST',
    body: { action, expectedVersion },
    key: idempotencyKey,
    ...options,
  })
}

export async function listStudySessions(params = {}, options = {}) {
  const query = new URLSearchParams()
  if (params.taskId) query.set('taskId', params.taskId)
  if (params.page) query.set('page', String(params.page))
  if (params.limit) query.set('limit', String(params.limit))
  const qs = query.toString()
  const path = qs ? `/study-sessions?${qs}` : '/study-sessions'
  return request(path, options)
}

export async function getGoalStudyTime(goalId, options = {}) {
  return request(`/goals/${encodeURIComponent(goalId)}/study-time`, options)
}
