import { getCsrfToken } from './auth.js'

export class CalendarApiError extends Error {
  constructor(message, status, code, context) { super(message); this.name = 'CalendarApiError'; this.status = status; this.code = code; this.context = context }
}
async function request(path, { method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (method !== 'GET') headers['X-CSRF-TOKEN'] = await getCsrfToken()
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`/api/v1/calendar${path}`, {
    method, credentials: 'same-origin', headers, signal,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
  if (response.status === 204) return null
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new CalendarApiError(payload?.error?.message || 'Không thể kết nối Google Calendar.', response.status, payload?.error?.code, payload?.error?.context)
  if (payload?.data == null) throw new CalendarApiError('Phản hồi lịch không hợp lệ.', 502, 'INVALID_RESPONSE')
  return payload.data
}
export const calendarStatus = options => request('/status', options)
export const calendarList = options => request('/calendars', options)
export const connectCalendar = () => request('/connection', { method: 'POST', body: {} })
export const disconnectCalendar = () => request('/connection', { method: 'DELETE' })
export const calendarSyncStatus = options => request('/sync', options)
export const retryCalendarSync = () => request('/sync/retry', { method: 'POST', body: {} })
export const saveCalendarSettings = settings => request('/settings', { method: 'PATCH', body: settings })
export const previewCalendar = (from, goalId) => request('/previews', { method: 'POST', body: { from, ...(goalId ? { goalId } : {}) } })
export const confirmCalendar = id => request(`/previews/${encodeURIComponent(id)}/confirm`, { method: 'POST', body: {} })
export function googleAuthorizationUrl(value) {
  const url = new URL(value)
  if (url.protocol !== 'https:' || url.hostname !== 'accounts.google.com') throw new Error('Địa chỉ kết nối Google không hợp lệ.')
  return url.href
}
export function eventLink(value) {
  try { const url = new URL(value); return url.protocol === 'https:' && ['www.google.com', 'calendar.google.com'].includes(url.hostname) ? url.href : null } catch { return null }
}

/** Keep unavailable selections visible; never silently exclude calendars from conflict checks. */
export function calendarSelection(settings, calendars) {
  const available = new Map(calendars.map(calendar => [calendar.id, calendar]))
  return {
    missingBusyCalendarIds: settings.busyCalendarIds.filter(id => !available.has(id)),
    targetUsable: Boolean(available.get(settings.targetCalendarId)?.writable),
  }
}
