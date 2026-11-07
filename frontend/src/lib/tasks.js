import { getCsrfToken } from './auth.js'

const BASE = '/api/v1'
const PAGE_LIMIT = 100

export class TaskApiError extends Error {
  constructor(message, { status, code, fieldErrors = [] } = {}) {
    super(message)
    this.name = 'TaskApiError'
    this.status = status
    this.code = code
    // Items look like { field, issue, message } (the API's error.details).
    this.fieldErrors = Array.isArray(fieldErrors) ? fieldErrors : []
  }
}

export function getTodayString() {
  const d = new Date()
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** Returns the parsed `{ data, meta }` envelope (data is null for 204). Throws TaskApiError on failure. */
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
    throw new TaskApiError(error?.message || `Không thể thực hiện yêu cầu (HTTP ${response.status}).`, {
      status: response.status,
      code: error?.code,
      fieldErrors: error?.details,
    })
  }
  return payload ?? { data: null }
}

/** Summary used by the daily page; counts come from the full list returned by the server. */
export function summarizeTasks(items) {
  const completedCount = items.filter(t => t.completed).length
  return {
    items,
    totalCount: items.length,
    completedCount,
    remainingCount: items.length - completedCount,
    totalEstimatedMinutes: items.reduce((sum, t) => sum + (t.estimatedMinutes || 0), 0),
  }
}

/** Tasks of one calendar day across the user's goals (GET /api/v1/tasks?date=, every page). */
export async function listTasksByDate(date = getTodayString(), { signal } = {}) {
  const items = []
  for (let page = 1; ; page += 1) {
    const { data, meta } = await request(
      `${BASE}/tasks?date=${encodeURIComponent(date)}&page=${page}&limit=${PAGE_LIMIT}`,
      { signal },
    )
    if (!Array.isArray(data) || typeof meta?.totalPages !== 'number') {
      throw new TaskApiError('Phản hồi danh sách công việc không đúng định dạng.', { status: 502 })
    }
    items.push(...data)
    if (page >= meta.totalPages) {
      return { date, ...summarizeTasks(items) }
    }
  }
}

/**
 * Marks a task done or re-opens it: PATCH /api/v1/goals/{goalId}/tasks/{taskId} with the target state
 * (never a toggle, so a repeated request cannot flip the state). Returns the task as stored.
 */
export async function setTaskCompleted(goalId, taskId, completed, { signal } = {}) {
  const { data } = await request(
    `${BASE}/goals/${encodeURIComponent(goalId)}/tasks/${encodeURIComponent(taskId)}`,
    { method: 'PATCH', body: { completed: Boolean(completed) }, signal },
  )
  if (!data || typeof data.id !== 'string') {
    throw new TaskApiError('Phản hồi công việc không đúng định dạng.', { status: 502 })
  }
  return data
}

/** POST /api/v1/goals/{goalId}/tasks. The server side belongs to ASP-10; until then this fails with an error. */
export async function createTask(goalId, { title, scheduledDate, estimatedMinutes }, { signal } = {}) {
  const { data } = await request(`${BASE}/goals/${encodeURIComponent(goalId)}/tasks`, {
    method: 'POST',
    body: { title, scheduledDate, estimatedMinutes: Number(estimatedMinutes) },
    signal,
  })
  return data
}

/** DELETE /api/v1/goals/{goalId}/tasks/{taskId}. The server side belongs to ASP-10. */
export async function deleteTask(goalId, taskId, { signal } = {}) {
  await request(`${BASE}/goals/${encodeURIComponent(goalId)}/tasks/${encodeURIComponent(taskId)}`, {
    method: 'DELETE',
    signal,
  })
  return true
}

/** There is no overdue endpoint yet (ASP-11), so nothing is reported instead of inventing data. */
export async function listOverdueTasks() {
  return []
}
