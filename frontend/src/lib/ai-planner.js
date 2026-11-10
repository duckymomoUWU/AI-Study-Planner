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

export function generatePlanDraft(goalId, { signal } = {}) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts`, { method: 'POST', body: {}, signal })
}

export function getLatestPlanDraft(goalId, { signal } = {}) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts/latest`, { signal })
}

/** Retry with the same key and body after a lost response. */
export function confirmPlanDraft(goalId, draftId, tasks, expectedGoalVersion, idempotencyKey, { signal } = {}) {
  return request(`/api/v1/goals/${encodeURIComponent(goalId)}/plan-drafts/${encodeURIComponent(draftId)}/confirm`, {
    method: 'POST', signal, key: idempotencyKey,
    body: { expectedGoalVersion, tasks: tasks.map(({ title, scheduledDate, estimatedMinutes }) => ({
      title, scheduledDate, estimatedMinutes: Number(estimatedMinutes),
    })) },
  })
}

/** Stable priority order for review; dates/minutes remain unchanged until explicit confirmation. */
export function sortDraftTasksByPriority(items) {
  const rank = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  return [...items].sort((a, b) => (rank[a.priority] ?? 1) - (rank[b.priority] ?? 1))
}
