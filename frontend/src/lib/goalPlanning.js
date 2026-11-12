import { createGoal } from './goals.js'
import { generatePlanDraft } from './ai-planner.js'

export function vietnamToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now)
  const value = type => parts.find(part => part.type === type).value
  return `${value('year')}-${value('month')}-${value('day')}`
}

export function startDateError(startDate, editing = false, today = vietnamToday()) {
  return !editing && startDate && startDate < today ? 'Ngày bắt đầu không được trước hôm nay.' : ''
}

/** Goal creation commits first. AI failure must not cause another goal POST or automatic confirmation. */
export async function createGoalWithPlan(input, { create = createGoal, generate = generatePlanDraft, onCreated = () => {}, planningScope } = {}) {
  const goal = await create(input)
  onCreated(goal)
  try {
    return { goal, draft: await generate(goal.id, planningScope ? { scope: planningScope } : undefined), planError: null }
  } catch (planError) {
    return { goal, draft: null, planError }
  }
}

export function firstPlannedDate(items) {
  return items.map(item => item.scheduledDate).filter(day => typeof day === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(day)).sort()[0]
}
