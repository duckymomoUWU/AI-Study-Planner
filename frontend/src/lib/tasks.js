import { getCsrfToken } from './auth.js'

export class TaskApiError extends Error {
  constructor(message, { status, code, fieldErrors = [] } = {}) {
    super(message)
    this.name = 'TaskApiError'
    this.status = status
    this.code = code
    this.fieldErrors = Array.isArray(fieldErrors) ? fieldErrors : []
  }
}

function getTodayString() {
  const d = new Date()
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getYesterdayString() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// In-memory placeholder state for frontend demo when backend API is pending
let placeholderTasks = [
  {
    id: 'placeholder-task-1',
    goalId: 'sample-goal-1',
    goalSubject: 'Giải tích 2',
    title: 'Ôn lại lý thuyết Tích phân đường loại 1 & 2',
    scheduledDate: getTodayString(),
    estimatedMinutes: 60,
    completed: true,
    source: 'manual',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'placeholder-task-2',
    goalId: 'sample-goal-1',
    goalSubject: 'Giải tích 2',
    title: 'Làm 5 bài tập phương trình vi phân cấp 2',
    scheduledDate: getTodayString(),
    estimatedMinutes: 90,
    completed: false,
    source: 'ai',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'placeholder-task-3',
    goalId: 'sample-goal-2',
    goalSubject: 'Cơ sở dữ liệu',
    title: 'Thực hành chuẩn hóa BCNF và 3NF các quan hệ',
    scheduledDate: getTodayString(),
    estimatedMinutes: 45,
    completed: false,
    source: 'manual',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'placeholder-task-overdue-1',
    goalId: 'sample-goal-1',
    goalSubject: 'Giải tích 2',
    title: 'Đọc trước định lý Green và công thức Stokes',
    scheduledDate: getYesterdayString(),
    estimatedMinutes: 60,
    completed: false,
    source: 'manual',
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
    throw new TaskApiError(payload?.message || `Không thể thực hiện yêu cầu (HTTP ${response.status}).`, {
      status: response.status,
      code: payload?.code,
      fieldErrors: payload?.fieldErrors,
    })
  }

  return payload
}

/**
 * US-03: Lấy danh sách công việc theo ngày
 * Gọi GET /api/tasks?date=YYYY-MM-DD
 * Nếu backend trả về 404 (chưa triển khai), sử dụng placeholder tasks để demo UI.
 */
export async function listTasksByDate(date = getTodayString(), { signal } = {}) {
  try {
    const payload = await request(`/api/tasks?date=${encodeURIComponent(date)}`, { signal })
    if (Array.isArray(payload?.items)) {
      return {
        date: payload.date || date,
        totalEstimatedMinutes: payload.totalEstimatedMinutes ?? payload.items.reduce((sum, t) => sum + (t.estimatedMinutes || 0), 0),
        completedCount: payload.completedCount ?? payload.items.filter(t => t.completed).length,
        totalCount: payload.totalCount ?? payload.items.length,
        items: payload.items,
        isPlaceholder: false,
      }
    }
  } catch (error) {
    if (error instanceof TaskApiError && error.status === 401) {
      throw error
    }
    // Fallback to placeholder tasks for UI demonstration when backend is pending (HTTP 404 / 502 / network failure)
  }

  const items = placeholderTasks.filter(t => t.scheduledDate === date)
  const totalEstimatedMinutes = items.reduce((sum, t) => sum + (t.estimatedMinutes || 0), 0)
  const completedCount = items.filter(t => t.completed).length

  return {
    date,
    totalEstimatedMinutes,
    completedCount,
    totalCount: items.length,
    items,
    isPlaceholder: true,
  }
}

/**
 * US-03: Đánh dấu trạng thái hoàn thành / mở lại task
 * Gọi PATCH /api/tasks/{taskId}/complete
 */
export async function toggleTaskComplete(taskId, completed, { signal } = {}) {
  try {
    const payload = await request(`/api/tasks/${encodeURIComponent(taskId)}/complete`, {
      method: 'PATCH',
      body: { completed },
      signal,
    })
    return payload
  } catch (error) {
    if (error instanceof TaskApiError && error.status === 401) {
      throw error
    }
    // Update local placeholder state
    const task = placeholderTasks.find(t => t.id === taskId)
    if (task) {
      task.completed = completed
      return { ...task }
    }
    throw error
  }
}

/**
 * US-03: Tạo công việc thủ công mới
 * Gọi POST /api/goals/{goalId}/tasks
 */
export async function createTask(goalId, { title, scheduledDate, estimatedMinutes }, { signal } = {}) {
  const body = { title, scheduledDate, estimatedMinutes: Number(estimatedMinutes) }
  try {
    const payload = await request(`/api/goals/${encodeURIComponent(goalId)}/tasks`, {
      method: 'POST',
      body,
      signal,
    })
    return payload
  } catch (error) {
    if (error instanceof TaskApiError && error.status === 401) {
      throw error
    }
    // Add to local placeholder state
    const newTask = {
      id: `task-manual-${Date.now()}`,
      goalId,
      goalSubject: 'Môn học',
      title,
      scheduledDate,
      estimatedMinutes: Number(estimatedMinutes),
      completed: false,
      source: 'manual',
      createdAt: new Date().toISOString(),
    }
    placeholderTasks.push(newTask)
    return newTask
  }
}

/**
 * US-03: Cập nhật chi tiết công việc
 * Gọi PATCH /api/tasks/{taskId}
 */
export async function updateTask(taskId, { title, scheduledDate, estimatedMinutes, completed } = {}, { signal } = {}) {
  const body = {}
  if (title !== undefined) body.title = title
  if (scheduledDate !== undefined) body.scheduledDate = scheduledDate
  if (estimatedMinutes !== undefined) body.estimatedMinutes = Number(estimatedMinutes)
  if (completed !== undefined) body.completed = completed

  try {
    const payload = await request(`/api/tasks/${encodeURIComponent(taskId)}`, {
      method: 'PATCH',
      body,
      signal,
    })
    return payload
  } catch (error) {
    if (error instanceof TaskApiError && error.status === 401) {
      throw error
    }
    const task = placeholderTasks.find(t => t.id === taskId)
    if (task) {
      Object.assign(task, body)
      return { ...task }
    }
    throw error
  }
}

/**
 * US-03: Xóa công việc
 * Gọi DELETE /api/tasks/{taskId}
 */
export async function deleteTask(taskId, { signal } = {}) {
  try {
    await request(`/api/tasks/${encodeURIComponent(taskId)}`, {
      method: 'DELETE',
      signal,
    })
    return true
  } catch (error) {
    if (error instanceof TaskApiError && error.status === 401) {
      throw error
    }
    placeholderTasks = placeholderTasks.filter(t => t.id !== taskId)
    return true
  }
}

/**
 * US-03: Lấy danh sách công việc trễ hạn (scheduledDate < today và completed == false)
 * Gọi GET /api/tasks/overdue
 */
export async function listOverdueTasks({ signal } = {}) {
  const today = getTodayString()
  try {
    const payload = await request('/api/tasks/overdue', { signal })
    if (Array.isArray(payload?.items)) {
      return payload.items
    }
  } catch (error) {
    if (error instanceof TaskApiError && error.status === 401) {
      throw error
    }
  }

  return placeholderTasks.filter(t => t.scheduledDate < today && !t.completed)
}
