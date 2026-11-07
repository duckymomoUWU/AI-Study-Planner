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

// In-memory placeholder goals when backend Goal API (Dev 2) is pending
let placeholderGoals = [
  {
    id: 'placeholder-goal-1',
    subject: 'Giải tích 2',
    topics: 'Tích phân đường, mặt; phương trình vi phân cấp 2',
    startDate: '2026-11-09',
    examDate: '2026-12-05',
    minutesPerDay: 90,
    timezone: 'Asia/Ho_Chi_Minh',
    version: 0,
    createdAt: new Date().toISOString(),
  },
  {
    id: 'placeholder-goal-2',
    subject: 'Cơ sở dữ liệu',
    topics: 'Mô hình ER, SQL, chuẩn hóa 1NF - BCNF, transaction',
    startDate: '2026-11-10',
    examDate: '2026-12-20',
    minutesPerDay: 60,
    timezone: 'Asia/Ho_Chi_Minh',
    version: 0,
    createdAt: new Date().toISOString(),
  },
]

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
    // Fallback to placeholder goals when backend is not yet available
    if (typeof window !== 'undefined' && error instanceof GoalApiError && error.status === 404) {
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
    if (typeof window !== 'undefined' && error instanceof GoalApiError && error.status === 404) {
      const newGoal = {
        id: `goal-local-${Date.now()}`,
        ...input,
        version: 0,
        timezone: 'Asia/Ho_Chi_Minh',
        createdAt: new Date().toISOString(),
      }
      placeholderGoals.push(newGoal)
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
    if (typeof window !== 'undefined' && error instanceof GoalApiError && error.status === 404) {
      const index = placeholderGoals.findIndex(g => g.id === goalId)
      if (index !== -1) {
        placeholderGoals[index] = {
          ...placeholderGoals[index],
          ...input,
          version: (placeholderGoals[index].version || 0) + 1,
        }
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
    if (typeof window !== 'undefined' && error instanceof GoalApiError && error.status === 404) {
      placeholderGoals = placeholderGoals.filter(g => g.id !== goalId)
      return null
    }
    throw error
  }
}
