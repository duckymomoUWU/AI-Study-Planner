import test from 'node:test'
import assert from 'node:assert/strict'
import { topicsFromText, studyDates, selectedOutlineTopics, requestRevision, applySyllabusFields, planningScopeForForm, savedPlanningScope } from './planningScope.js'
import { generatePlanDraft, confirmPlanDraft } from './ai-planner.js'
import { clearCsrfTokenCache } from './auth.js'

test('reload, goal editing and regenerate preserve saved zero-day budgets, priority and sources', () => {
  const scope = { selectedTopics: [{ id: 'a', title: 'Algebra', priority: 'HIGH', sources: [{ page: 2, section: 'Chapter 1' }] }], dailyMinutes: { '2026-11-12': 0, '2026-11-13': 30 } }
  const goal = JSON.parse(JSON.stringify({ topics: 'Algebra', planningScope: scope }))
  const saved = savedPlanningScope(goal)
  assert.deepEqual(saved, scope)
  assert.deepEqual(planningScopeForForm({ topics: 'Algebra', startDate: '2026-11-11', examDate: '2026-11-20' }, saved.selectedTopics, saved.dailyMinutes), scope)
  assert.deepEqual(goal.planningScope, scope)
})

test('changing the date range removes only overrides outside the new range and never a zero value inside', () => {
  const next = planningScopeForForm({ topics: 'Algebra', startDate: '2026-11-12', examDate: '2026-11-14' }, [], { '2026-11-11': 30, '2026-11-12': 0, '2026-11-14': 0 })
  assert.deepEqual(next.dailyMinutes, { '2026-11-12': 0 })
  assert.equal(next.selectedTopics[0].title, 'Algebra')
})

test('only confirmed Collision and Path finding cross the API, with source and priority intact', async t => {
  const chapters = [{ id: 'c2', title: 'Chapter 2', topics: [
    { id: 'vector', title: 'Vector', sources: [] },
    { id: 'collision', title: 'Collision', sources: [{ page: 6, section: 'Chapter 2' }], priority: 'HIGH' },
    { id: 'path', title: 'Path finding', sources: [{ page: 7, section: 'Chapter 4' }] },
  ] }]
  const selectedTopics = selectedOutlineTopics(chapters, ['collision', 'path'])
  assert.deepEqual(selectedTopics.map(topic => topic.id), ['collision', 'path'])
  clearCsrfTokenCache()
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ data: { token: 'token' } })
    assert.deepEqual(JSON.parse(options.body), { selectedTopics, dailyMinutes: { '2026-11-10': 0 } })
    return Response.json({ data: { id: 'draft', proposal: { tasks: [] } } }, { status: 201 })
  })
  await generatePlanDraft('goal', { scope: { selectedTopics, dailyMinutes: { '2026-11-10': 0 } } })
})

test('confirm preserves topic/task IDs and edited estimates, strips review-only fields', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.deepEqual(JSON.parse(options.body), { expectedGoalVersion: 1, tasks: [{ id: 'work-1', topicId: 'collision', title: 'Explain collision', scheduledDate: '2026-11-11', estimatedMinutes: 45 }] })
    return Response.json({ data: { createdCount: 1 } })
  })
  await confirmPlanDraft('goal', 'draft', [{ id: 'work-1', topicId: 'collision', title: 'Explain collision', scheduledDate: '2026-11-11', estimatedMinutes: '45', requiresContinuousSession: true, priority: 'HIGH' }], 1, 'key')
})

test('study dates exclude exam day, use calendar days and cap at 90', () => {
  assert.deepEqual(studyDates('2026-11-10', '2026-11-13'), ['2026-11-10', '2026-11-11', '2026-11-12'])
  assert.deepEqual(studyDates('2026-11-10', '2026-11-10'), [])
  assert.equal(studyDates('2026-01-01', '2027-01-01').length, 90)
})

test('manual topics do not introduce any generic or fabricated subjects', () => {
  assert.deepEqual(topicsFromText(''), [])
  assert.deepEqual(topicsFromText('Collision\nPath finding; Collision').map(topic => topic.title), ['Collision', 'Path finding'])
})

test('confirmed syllabus date autofills the form; absent date preserves manual input and personal budget', () => {
  const form = { subject: '', topics: '', examDate: '2026-12-10', startDate: '2026-11-10', minutesPerDay: '45' }
  const fields = { subject: 'Game', topics: 'Collision', examDate: '2026-12-25', minutesPerDay: 60 }
  const filled = applySyllabusFields(form, fields)
  assert.equal(filled.examDate, '2026-12-25')
  assert.equal(filled.minutesPerDay, '45')
  assert.equal(filled.startDate, '2026-11-10')
  assert.equal(applySyllabusFields(form, { ...fields, examDate: null }).examDate, '2026-12-10')
  assert.equal(form.examDate, '2026-12-10')
})

test('slow previous response cannot overwrite newer input/draft, including ignored cancellation', async () => {
  const gate = requestRevision()
  let completeOld
  let visible = { examDate: '2026-11-20', draft: 'saved' }
  const oldTicket = gate.next()
  const pending = new Promise(resolve => { completeOld = resolve }).then(draft => {
    if (gate.isCurrent(oldTicket)) visible = { ...visible, draft }
  })
  gate.next()
  visible = { examDate: '2026-11-25', draft: 'new-input-draft' }
  completeOld('old-response')
  await pending
  assert.deepEqual(visible, { examDate: '2026-11-25', draft: 'new-input-draft' })
})
