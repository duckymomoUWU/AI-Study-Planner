import test from 'node:test'
import assert from 'node:assert/strict'
import { clearCsrfTokenCache } from './auth.js'
import { calendarStatus, calendarSyncStatus, retryCalendarSync, previewCalendar, confirmCalendar, saveCalendarSettings, googleAuthorizationUrl, eventLink, CalendarApiError, calendarSelection } from './googleCalendar.js'

test('busy calendar errors preserve failed IDs so UI can name the calendars without ignoring them', async t => {
  clearCsrfTokenCache()
  t.mock.method(globalThis, 'fetch', async url => url.endsWith('/csrf') ? Response.json({ data: { token: 'csrf' } })
    : Response.json({ error: { code: 'CALENDAR_BUSY_UNAVAILABLE', message: 'Unavailable', context: { failedCalendarIds: ['holiday'] } } }, { status: 502 }))
  await assert.rejects(previewCalendar('2026-11-11'), error => error.code === 'CALENDAR_BUSY_UNAVAILABLE' && error.context.failedCalendarIds[0] === 'holiday')
})

test('sync status and retry use session APIs without falsely claiming remote changes are complete', async t => {
  clearCsrfTokenCache()
  const methods = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'csrf' } })
    assert.equal(options.credentials, 'same-origin')
    methods.push(options.method)
    if (url.endsWith('/retry')) { assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf'); assert.equal(options.method, 'POST') }
    else assert.equal(url, '/api/v1/calendar/sync')
    return Response.json({ data: { pending: 1, blocked: 0, synced: 0, deleted: 0, items: [] } })
  })
  assert.equal((await calendarSyncStatus()).pending, 1)
  assert.equal((await retryCalendarSync()).synced, 0)
  assert.deepEqual(methods, ['GET', 'POST'])
})

test('preview reads busy slots without any confirmation; only explicit confirm calls export', async t => {
  clearCsrfTokenCache()
  const requests = []
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'csrf' } })
    requests.push(url)
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf')
    if (url.endsWith('/previews')) {
      assert.deepEqual(JSON.parse(options.body), { from: '2026-11-09', goalId: 'g1' })
      return Response.json({ data: { id: 'p1', slots: [] } }, { status: 201 })
    }
    assert.equal(url, '/api/v1/calendar/previews/p1/confirm')
    return Response.json({ data: { confirmed: 1, failed: 0 } })
  })
  assert.equal((await previewCalendar('2026-11-09', 'g1')).id, 'p1')
  assert.equal(requests.length, 1)
  await confirmCalendar('p1'); await confirmCalendar('p1')
  assert.equal(requests[1], requests[2])
})
test('settings are sent only to backend and calendar errors are not turned into free slots', async t => {
  clearCsrfTokenCache()
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'csrf' } })
    assert.equal(options.method, 'PATCH')
    return Response.json({ error: { code: 'CALENDAR_BUSY_UNAVAILABLE', message: 'Busy data unavailable' } }, { status: 502 })
  })
  await assert.rejects(saveCalendarSettings({}), error => error instanceof CalendarApiError && error.code === 'CALENDAR_BUSY_UNAVAILABLE')
})
test('missing configuration is visible without fake connection or credentials', async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ data: { configured: false, connected: false } }))
  assert.deepEqual(await calendarStatus(), { configured: false, connected: false })
})
test('OAuth navigation and event links accept only expected HTTPS hosts', () => {
  assert.equal(googleAuthorizationUrl('https://accounts.google.com/o/oauth2/v2/auth'), 'https://accounts.google.com/o/oauth2/v2/auth')
  assert.throws(() => googleAuthorizationUrl('https://example.com/'))
  assert.throws(() => googleAuthorizationUrl('javascript:alert(1)'))
  assert.equal(eventLink('javascript:alert(1)'), null)
  assert.equal(eventLink('https://calendar.google.com/calendar/event'), 'https://calendar.google.com/calendar/event')
})
test('stale selected busy calendars stay visible until the user explicitly removes them', () => {
  const settings = { targetCalendarId: 'study', busyCalendarIds: ['work', 'disappeared'] }
  const calendars = [{ id: 'study', writable: true }, { id: 'work', writable: false }]
  const result = calendarSelection(settings, calendars)
  assert.equal(result.targetUsable, true)
  assert.deepEqual(result.missingBusyCalendarIds, ['disappeared'])
  assert.deepEqual(settings.busyCalendarIds, ['work', 'disappeared'])
  const afterUserUnchecks = { ...settings, busyCalendarIds: ['work'] }
  assert.deepEqual(calendarSelection(afterUserUnchecks, calendars).missingBusyCalendarIds, [])
  assert.equal(calendarSelection(settings, [{ id: 'study', writable: false }]).targetUsable, false)
})
