import { getCsrfToken } from './auth.js'

export class GoalApiError extends Error {
  constructor(message, { status, code, fieldErrors = [] } = {}) {
    super(message)
    this.name = 'GoalApiError'
    this.status = status
    this.code = code
    this.fieldErrors = Array.isArray(fieldErrors) ? fieldErrors : []
  }
}

async function request(path, { method = 'GET', body, signal, csrfToken } = {}) {
  const headers = { Accept: 'application/json' }
  const options = {
    method,
    headers,
    signal,
    credentials: 'same-origin',
  }

  if (method !== 'GET') {
    const token = csrfToken ?? (typeof window !== 'undefined' ? await getCsrfToken().catch(() => null) : null)
    if (token) {
      headers['X-CSRF-TOKEN'] = token
    }
  }

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(body)
  }

  const response = await fetch(path, options)
  let payload = null
  if (response.status !== 204) {
    const text = await response.text()
    if (text) {
      try {
        payload = JSON.parse(text)
      } catch {
        payload = { message: text }
      }
    }
  }

  if (!response.ok) {
    throw new GoalApiError(payload?.message || `Không thể thực hiện yêu cầu (HTTP ${response.status}).`, {
      status: response.status,
      code: payload?.code,
      fieldErrors: payload?.fieldErrors,
    })
  }
  return payload
}

export async function listGoals({ signal } = {}) {
  const payload = await request('/api/goals', { signal })
  if (Array.isArray(payload?.items)) {
    return payload.items
  }
  throw new GoalApiError('Phản hồi danh sách mục tiêu không đúng định dạng.', { status: 502 })
}

export async function getGoal(goalId, { signal } = {}) {
  const payload = await request(`/api/goals/${encodeURIComponent(goalId)}`, { signal })
  if (!payload || typeof payload.id !== 'string') {
    throw new GoalApiError('Phản hồi chi tiết mục tiêu không đúng định dạng.', { status: 502 })
  }
  return payload
}

export async function createGoal(input, { signal } = {}) {
  return request('/api/goals', { method: 'POST', body: input, signal })
}

export async function updateGoal(goalId, input, { signal } = {}) {
  return request(`/api/goals/${encodeURIComponent(goalId)}`, { method: 'PUT', body: input, signal })
}

export async function deleteGoal(goalId, { signal } = {}) {
  return request(`/api/goals/${encodeURIComponent(goalId)}`, { method: 'DELETE', signal })
}
