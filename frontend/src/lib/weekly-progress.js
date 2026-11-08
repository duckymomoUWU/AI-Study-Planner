const BASE = '/api/v1'
const DAY_MS = 24 * 60 * 60 * 1000
const WEEKDAYS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']

export class WeeklyProgressApiError extends Error {
  constructor(message, { status, code } = {}) {
    super(message)
    this.name = 'WeeklyProgressApiError'
    this.status = status
    this.code = code
  }
}

// Dates are plain calendar dates (YYYY-MM-DD). Arithmetic uses UTC midnight so a local DST change never moves a day.
function toUtc(dateStr) {
  const [y, m, d] = dateStr.split('-').map(Number)
  return Date.UTC(y, m - 1, d)
}

function fromUtc(ms) {
  const date = new Date(ms)
  const y = date.getUTCFullYear()
  const m = String(date.getUTCMonth() + 1).padStart(2, '0')
  const d = String(date.getUTCDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function todayString(now = new Date()) {
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** Monday of the week that contains the date (weeks run Monday to Sunday). */
export function startOfWeek(dateStr) {
  const ms = toUtc(dateStr)
  const mondayBased = (new Date(ms).getUTCDay() + 6) % 7 // Monday = 0 ... Sunday = 6
  return fromUtc(ms - mondayBased * DAY_MS)
}

/** The same weekday `weeks` weeks later (negative for earlier). */
export function shiftWeek(dateStr, weeks) {
  return fromUtc(toUtc(dateStr) + weeks * 7 * DAY_MS)
}

/** "02/11 – 08/11/2026" for the week that starts on `startStr`. */
export function formatWeekRange(startStr, endStr) {
  const [sy, sm, sd] = startStr.split('-')
  const [ey, em, ed] = endStr.split('-')
  const start = sy === ey ? `${sd}/${sm}` : `${sd}/${sm}/${sy}`
  return `${start} – ${ed}/${em}/${ey}`
}

/** GET /api/v1/progress/weekly?date=YYYY-MM-DD: counts of the user's tasks scheduled in that week. */
export async function getWeeklyProgress(date, { signal } = {}) {
  const query = date ? `?date=${encodeURIComponent(date)}` : ''
  const response = await fetch(`${BASE}/progress/weekly${query}`, {
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
    throw new WeeklyProgressApiError(error?.message || `Không thể tải tiến độ tuần (HTTP ${response.status}).`, {
      status: response.status,
      code: error?.code,
    })
  }
  const data = payload?.data
  if (!data || !Number.isInteger(data.totalTasks) || !Number.isInteger(data.completedTasks)
    || typeof data.weekStart !== 'string' || typeof data.weekEnd !== 'string') {
    throw new WeeklyProgressApiError('Phản hồi tiến độ tuần không đúng định dạng.', { status: 502 })
  }
  return data
}

/**
 * What the UI shows for one week. Progress is only completed / total tasks of that week, with no judgement of
 * how well the student understands the material. A week without tasks is 0/0 with 0 percent (not an error).
 */
export function describeWeeklyProgress(data) {
  const total = data.totalTasks
  const completed = data.completedTasks
  const percent = total === 0 ? 0 : Math.round((completed / total) * 100)
  const byDate = new Map((Array.isArray(data.days) ? data.days : []).map(day => [day.date, day]))
  const days = WEEKDAYS.map((label, index) => {
    const date = fromUtc(toUtc(data.weekStart) + index * DAY_MS)
    const day = byDate.get(date)
    const dayTotal = day?.totalTasks ?? 0
    const dayCompleted = day?.completedTasks ?? 0
    return {
      date,
      label,
      total: dayTotal,
      completed: dayCompleted,
      percent: dayTotal === 0 ? 0 : Math.round((dayCompleted / dayTotal) * 100),
    }
  })
  return {
    label: `${completed}/${total}`,
    percent,
    completed,
    total,
    remaining: Number.isInteger(data.remainingTasks) ? data.remainingTasks : total - completed,
    isEmpty: total === 0,
    range: formatWeekRange(data.weekStart, data.weekEnd),
    weekStart: data.weekStart,
    days,
  }
}

export function describeWeeklyError(error) {
  if (error instanceof WeeklyProgressApiError) {
    if (error.status === 401) return 'Bạn cần đăng nhập để xem tiến độ theo tuần.'
    if (error.status === 400) return 'Ngày được chọn không hợp lệ.'
    return error.message
  }
  return error?.message || 'Không thể tải tiến độ tuần.'
}
