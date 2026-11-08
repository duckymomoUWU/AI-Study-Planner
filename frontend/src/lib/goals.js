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

const LEGACY_STORAGE_KEY = 'ai_study_planner_placeholder_goals'

/** Removes goals that older builds kept in the browser. Goals now only come from the server. */
export function clearPlaceholderGoals() {
  if (typeof window !== 'undefined') {
    try {
      localStorage.removeItem(LEGACY_STORAGE_KEY)
    } catch {
      // ignore
    }
  }
}

const isNetworkFailure = error =>
  error instanceof TypeError ||
  (error instanceof GoalApiError && [502, 503, 504].includes(error.status))

/** POST /api/v1/goals/parse-syllabus: autofill a goal form from a syllabus (Gemini on the server). */
export async function parseSyllabusDocument({ content, base64, mimeType, fileName } = {}) {
  try {
    const { data } = await request(`${BASE}/parse-syllabus`, {
      method: 'POST',
      body: { content, base64, mimeType, fileName },
    })
    return data
  } catch (error) {
    if (typeof window !== 'undefined' && isNetworkFailure(error)) {
      // Offline fallback from sprint 3: a rough guess from the file name or text, never saved by itself.
      const text = content || ''
      let subject = 'Môn học ôn tập'
      const sm = text.match(/(?:môn(?:\s*học)?|học\s*phần|subject|đề\s*cương(?:\s*môn)?|giáo\s*trình)\s*[:：-]\s*([^\r\n]+)/i)
      if (sm) {
        subject = sm[1].trim()
      } else if (fileName) {
        const nameOnly = fileName.replace(/\.[^.]+$/, '').replace(/(?:de_?cuong|syllabus|tai_?lieu)[_\-\s]*/gi, '').replace(/[_-]/g, ' ').trim()
        if (nameOnly) subject = nameOnly
      }

      const today = new Date()
      const startStr = today.toISOString().split('T')[0]
      const examDate = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1000)
      const examStr = examDate.toISOString().split('T')[0]

      return {
        subject: subject.slice(0, 100),
        topics: 'Tổng quan kiến thức cốt lõi, Các dạng bài tập trọng tâm, Luyện giải đề thi thực tế',
        startDate: startStr,
        examDate: examStr,
        minutesPerDay: 90,
      }
    }
    throw error
  }
}
