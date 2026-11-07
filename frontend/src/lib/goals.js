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

const STORAGE_KEY = 'ai_study_planner_placeholder_goals'

// Initial seed goals: Clean empty state for registered users (no mockup data)
function getInitialPlaceholderGoals() {
  if (typeof window !== 'undefined') {
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (saved) {
        const parsed = JSON.parse(saved)
        if (Array.isArray(parsed)) {
          // Exclude any legacy placeholder/mock goals
          return parsed.filter(g => !g.id.startsWith('placeholder-goal-'))
        }
      }
    } catch {
      // fallback
    }
  }
  return []
}

let placeholderGoals = getInitialPlaceholderGoals()

export function clearPlaceholderGoals() {
  placeholderGoals = []
  if (typeof window !== 'undefined') {
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // ignore
    }
  }
}

function savePlaceholderGoals() {
  if (typeof window !== 'undefined') {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(placeholderGoals))
    } catch {
      // ignore
    }
  }
}

const isBackendPending = (error) =>
  typeof window !== 'undefined' &&
  (
    (error instanceof GoalApiError && [404, 502, 503, 504].includes(error.status)) ||
    error instanceof TypeError
  )

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
  try {
    const payload = await request('/api/goals', { signal })
    if (Array.isArray(payload?.items)) {
      return payload.items
    }
    throw new GoalApiError('Phản hồi danh sách mục tiêu không đúng định dạng.', { status: 502 })
  } catch (error) {
    if (error instanceof GoalApiError && error.status === 401) {
      throw error
    }
    // Fallback to placeholder goals when backend is not yet available (404 / 502 proxy ECONNREFUSED)
    if (isBackendPending(error)) {
      return [...placeholderGoals]
    }
    throw error
  }
}

export async function createGoal(input, { signal } = {}) {
  try {
    return await request('/api/goals', { method: 'POST', body: input, signal })
  } catch (error) {
    if (error instanceof GoalApiError && error.status === 401) {
      throw error
    }
    if (isBackendPending(error)) {
      const newGoal = {
        id: `goal-local-${Date.now()}`,
        ...input,
        version: 0,
        timezone: 'Asia/Ho_Chi_Minh',
        createdAt: new Date().toISOString(),
      }
      placeholderGoals.push(newGoal)
      savePlaceholderGoals()
      return newGoal
    }
    throw error
  }
}

export async function updateGoal(goalId, input, { signal } = {}) {
  try {
    return await request(`/api/goals/${encodeURIComponent(goalId)}`, {
      method: 'PUT',
      body: input,
      signal,
    })
  } catch (error) {
    if (error instanceof GoalApiError && error.status === 401) {
      throw error
    }
    if (isBackendPending(error)) {
      const index = placeholderGoals.findIndex(g => g.id === goalId)
      if (index !== -1) {
        placeholderGoals[index] = {
          ...placeholderGoals[index],
          ...input,
          version: (placeholderGoals[index].version || 0) + 1,
        }
        savePlaceholderGoals()
        return placeholderGoals[index]
      }
    }
    throw error
  }
}

export async function deleteGoal(goalId, { signal } = {}) {
  try {
    return await request(`/api/goals/${encodeURIComponent(goalId)}`, {
      method: 'DELETE',
      signal,
    })
  } catch (error) {
    if (error instanceof GoalApiError && error.status === 401) {
      throw error
    }
    if (isBackendPending(error)) {
      placeholderGoals = placeholderGoals.filter(g => g.id !== goalId)
      savePlaceholderGoals()
      return null
    }
    throw error
  }
}
