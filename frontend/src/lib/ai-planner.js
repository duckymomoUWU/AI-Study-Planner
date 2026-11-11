import { getCsrfToken } from './auth.js'

export class PlanApiError extends Error {
  constructor(message, status, code, context) {
    super(message)
    this.name = 'PlanApiError'
    this.status = status
    this.code = code
    this.context = context
  }
}

async function request(path, { method = 'GET', body, signal, key } = {}) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') headers['X-CSRF-TOKEN'] = await getCsrfToken()
  if (key) headers['Idempotency-Key'] = key
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(path, {
    method, headers, credentials: 'same-origin', signal,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new PlanApiError(payload?.error?.message || `HTTP ${response.status}`,
    response.status, payload?.error?.code, payload?.error?.context)
  if (!payload?.data || typeof payload.data !== 'object') throw new PlanApiError('Invalid plan response.', 502)
  return payload.data
}

export function generatePlanDraft(goalId, { signal, scope } = {}) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts`, { method: 'POST', body: scope || {}, signal })
}

export function getLatestPlanDraft(goalId, { signal } = {}) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts/latest`, { signal })
}

/** Retry with the same key and body after a lost response. */
export function confirmPlanDraft(goalId, draftId, tasks, expectedGoalVersion, idempotencyKey, { signal, allowPartial = false, deferredTasks = [] } = {}) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts/${encodeURIComponent(draftId)}/confirm`, {
    method: 'POST', signal, key: idempotencyKey,
    body: { expectedGoalVersion, ...(allowPartial ? { allowPartial: true, deferredTasks: deferredTasks.map(({ id, topicId, title, estimatedMinutes }) => ({ id, topicId, title, estimatedMinutes: Number(estimatedMinutes) })) } : {}), tasks: tasks.map(({ id, topicId, title, scheduledDate, estimatedMinutes }) => ({
      ...(topicId ? { id, topicId } : {}),
      title, scheduledDate, estimatedMinutes: Number(estimatedMinutes),
    })) },
  })
}

export function getPendingPlanDrafts(goalId) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts/pending`)
}

export function reschedulePlanDraft(goalId, draftId) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts/${encodeURIComponent(draftId)}/reschedule`, { method: 'POST' })
}

export function draftReview(items, availableMinutes) {
  const scheduled = items.filter(item => item.scheduledDate)
  const remaining = items.filter(item => !item.scheduledDate)
  const requiredMinutes = items.reduce((sum, item) => sum + Number(item.estimatedMinutes || 0), 0)
  return { scheduled, remaining, requiredMinutes, shortfallMinutes: Math.max(0, requiredMinutes - availableMinutes),
    remainingMinutes: remaining.reduce((sum, item) => sum + Number(item.estimatedMinutes || 0), 0) }
}

export function draftState(value) {
  const status = value?.status || 'DRAFT'
  const messages = {
    DRAFT: 'Bản nháp chưa lưu vào lịch học. Hãy kiểm tra trước khi xác nhận.',
    CONFIRMED: 'Kế hoạch này đã được lưu thành lịch học. Bạn đang xem lại đề xuất lúc lưu; chỉnh công việc hiện tại trong Lịch học.',
    EXPIRED: 'Bản nháp đã hết hạn xác nhận. Bạn có thể xem lại hoặc tạo bản nháp mới; lịch học đã lưu không thay đổi.',
    STALE: 'Cấu hình hoặc công việc đã thay đổi từ lúc tạo bản nháp. Hãy xếp lại theo cấu hình mới trước khi lưu.',
  }
  return { readOnly: status !== 'DRAFT', canReschedule: status === 'STALE' && Boolean(value?.proposal.scope), message: messages[status] || 'Chưa thể xác nhận bản nháp này.' }
}

/** Stable priority order for review; dates/minutes remain unchanged until explicit confirmation. */
export function sortDraftTasksByPriority(items) {
  const rank = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  return [...items].sort((a, b) => (rank[a.priority] ?? 1) - (rank[b.priority] ?? 1))
}
