import test from 'node:test'
import assert from 'node:assert/strict'
import { createGoalWithPlan, firstPlannedDate, startDateError, vietnamToday } from './goalPlanning.js'

test('new goals reject yesterday, accept today and tomorrow; editing preserves historical starts', () => {
  assert.ok(startDateError('2026-11-09', false, '2026-11-10'))
  assert.equal(startDateError('2026-11-10', false, '2026-11-10'), '')
  assert.equal(startDateError('2026-11-11', false, '2026-11-10'), '')
  assert.equal(startDateError('2026-11-09', true, '2026-11-10'), '')
  assert.equal(vietnamToday(new Date('2026-11-09T17:00:00Z')), '2026-11-10')
  assert.equal(vietnamToday(new Date('2026-11-09T16:59:59Z')), '2026-11-09')
})

test('create commits a goal, notifies UI, then generates its draft exactly once without confirmation', async () => {
  const steps = [], goal = { id: 'g1', version: 0 }, draft = { id: 'd1', proposal: { tasks: [] } }
  const result = await createGoalWithPlan({ subject: 'SQL' }, {
    create: async input => { assert.equal(input.subject, 'SQL'); steps.push('create'); return goal },
    onCreated: stored => { assert.equal(stored, goal); steps.push('show-goal') },
    generate: async id => { assert.equal(id, 'g1'); steps.push('draft'); return draft },
  })
  assert.deepEqual(steps, ['create', 'show-goal', 'draft'])
  assert.deepEqual(result, { goal, draft, planError: null })
})

test('AI failure retains the created goal and never retries goal creation', async () => {
  const goal = { id: 'g1' }, error = Object.assign(new Error('AI unavailable'), { status: 503 })
  let posts = 0, notifications = 0
  const result = await createGoalWithPlan({}, {
    create: async () => { posts++; return goal },
    onCreated: () => { notifications++ },
    generate: async () => { throw error },
  })
  assert.equal(posts, 1); assert.equal(notifications, 1)
  assert.equal(result.goal, goal); assert.equal(result.planError, error); assert.equal(result.draft, null)
})

test('goal validation failure never starts AI generation', async () => {
  let generated = false
  await assert.rejects(createGoalWithPlan({}, {
    create: async () => { throw new Error('Invalid goal') },
    generate: async () => { generated = true },
  }), /Invalid goal/)
  assert.equal(generated, false)
})

test('calendar opens the earliest saved day even when priorities change task order', () => {
  assert.equal(firstPlannedDate([{ scheduledDate: '2026-11-15' }, { scheduledDate: '2026-11-11' }]), '2026-11-11')
  assert.equal(firstPlannedDate([]), undefined)
})

test('retry can update the same goal and forwards confirmed scope without another create', async () => {
  const original = { subject: 'Game', examDate: '2026-11-20' }
  const revised = { ...original, examDate: '2026-11-25' }
  const scope = { selectedTopics: [{ id: 'collision', title: 'Collision' }], dailyMinutes: { '2026-11-11': 0 } }
  const goal = { id: 'existing', version: 1 }
  let updated = 0
  const result = await createGoalWithPlan(revised, {
    create: async input => { updated++; assert.deepEqual(input, revised); return goal },
    planningScope: scope,
    generate: async (id, options) => { assert.equal(id, 'existing'); assert.deepEqual(options.scope, scope); return { id: 'draft-new' } },
  })
  assert.equal(updated, 1)
  assert.equal(result.goal.id, 'existing')
  assert.deepEqual(revised, { subject: 'Game', examDate: '2026-11-25' })
})
