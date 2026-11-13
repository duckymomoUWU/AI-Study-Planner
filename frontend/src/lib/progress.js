const BASE = '/api/v1'

export class ProgressApiError extends Error {
  constructor(message, { status, code } = {}) {
    super(message)
    this.name = 'ProgressApiError'
    this.status = status
    this.code = code
  }
}

/** GET /api/v1/goals/{goalId}/progress: counts of the goal's saved tasks (whole goal, not one page). */
export async function getGoalProgress(goalId, { signal } = {}) {
  const response = await fetch(`${BASE}/goals/${encodeURIComponent(goalId)}/progress`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
    signal,
  })
  let payload = null
  try {
    payload = await response.json()
  } catch {
    payload = null
  }
  if (!response.ok) {
    const error = payload?.error
    throw new ProgressApiError(error?.message || `Không thể tải tiến độ (HTTP ${response.status}).`, {
      status: response.status,
      code: error?.code,
    })
  }
  const data = payload?.data
  if (!data || !Number.isInteger(data.totalTasks) || !Number.isInteger(data.completedTasks)) {
    throw new ProgressApiError('Phản hồi tiến độ không đúng định dạng.', { status: 502 })
  }
  return data
}

/**
 * What the UI shows for a progress response. Progress is only "completed / total tasks": no judgement of how
 * well the student understands the material. With no tasks the label is 0/0 and the percentage is 0.
 */
export function describeProgress(progress) {
  const total = progress.totalTasks
  const completed = progress.completedTasks
  const remaining = Number.isInteger(progress.remainingTasks) ? progress.remainingTasks : total - completed
  const percent = total === 0 ? 0 : Math.round((completed / total) * 100)
  return {
    label: `${completed}/${total}`,
    percent,
    completed,
    remaining,
    total,
    isEmpty: total === 0,
  }
}

/** Message for a failed progress request. */
export function describeProgressError(error) {
  if (error instanceof ProgressApiError) {
    if (error.status === 401) return 'Bạn cần đăng nhập để xem tiến độ.'
    if (error.status === 404) return 'Không tìm thấy mục tiêu để tính tiến độ.'
    return error.message
  }
  return error?.message || 'Không thể tải tiến độ.'
}
