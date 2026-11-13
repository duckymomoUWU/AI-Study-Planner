import { getCsrfToken } from './auth.js'

async function request(query = '', body) {
  const response = await fetch(`/api/v1/study-budget${query}`, {
    method: body ? 'PUT' : 'GET', credentials: 'same-origin',
    headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': await getCsrfToken() } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw Object.assign(new Error(payload?.error?.message || 'Không tải được quỹ phút chung.'), { status: response.status, code: payload?.error?.code })
  if (!payload?.data || !Array.isArray(payload.data.days)) throw new Error('Dữ liệu quỹ phút chưa hợp lệ.')
  return payload.data
}
export function getStudyBudget(from, to) {
  return request(from && to ? `?${new URLSearchParams({ from, to })}` : '')
}
export function saveStudyBudget(settings, defaultMinutes, dailyMinutes) {
  return request('', { expectedVersion: settings.version, defaultMinutes: Number(defaultMinutes),
    dailyMinutes: Object.fromEntries(Object.entries(dailyMinutes).map(([day, value]) => [day, Number(value)])) })
}
