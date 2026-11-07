import { getCsrfToken } from './auth.js'

const BASE = '/api/v1/goals'
const PAGE_LIMIT = 100

export class GoalApiError extends Error {
  constructor(message, { status, code, fieldErrors = [] } = {}) {
    super(message)
    this.name = 'GoalApiError'
    this.status = status
    this.code = code
    // Items look like { field, issue, message } (the API's error.details).
    this.fieldErrors = Array.isArray(fieldErrors) ? fieldErrors : []
  }
}

/** Returns the parsed `{ data, meta }` envelope (data is null for 204). Throws GoalApiError on failure. */
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
        payload = null
      }
    }
  }

  if (!response.ok) {
    const error = payload?.error
    throw new GoalApiError(error?.message || `Không thể thực hiện yêu cầu (HTTP ${response.status}).`, {
      status: response.status,
      code: error?.code,
      fieldErrors: error?.details,
    })
  }
  return payload ?? { data: null }
}

/** All goals of the current user (follows the pagination of GET /api/v1/goals). */
export async function listGoals({ signal } = {}) {
  const items = []
  for (let page = 1; ; page += 1) {
    const { data, meta } = await request(`${BASE}?page=${page}&limit=${PAGE_LIMIT}`, { signal })
    if (!Array.isArray(data) || typeof meta?.totalPages !== 'number') {
      throw new GoalApiError('Phản hồi danh sách mục tiêu không đúng định dạng.', { status: 502 })
    }
    items.push(...data)
    if (page >= meta.totalPages) {
      return items
    }
  }
}

export async function getGoal(goalId, { signal } = {}) {
  const { data } = await request(`${BASE}/${encodeURIComponent(goalId)}`, { signal })
  if (!data || typeof data.id !== 'string') {
    throw new GoalApiError('Phản hồi chi tiết mục tiêu không đúng định dạng.', { status: 502 })
  }
  return data
}

/** input: { subject, topics, startDate, examDate, minutesPerDay, timezone? } */
export async function createGoal(input, { signal } = {}) {
  const { data } = await request(BASE, { method: 'POST', body: input, signal })
  return data
}

/** changes: any of the editable fields; expectedVersion is required by the server. */
export async function updateGoal(goalId, changes, expectedVersion, { signal } = {}) {
  const { data } = await request(`${BASE}/${encodeURIComponent(goalId)}`, {
    method: 'PATCH',
    body: { ...changes, expectedVersion },
    signal,
  })
  return data
}

export async function deleteGoal(goalId, { signal } = {}) {
  await request(`${BASE}/${encodeURIComponent(goalId)}`, { method: 'DELETE', signal })
  return null
}
