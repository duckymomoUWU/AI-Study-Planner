import { createGoal } from './goals.js'
import { generatePlanDraft } from './ai-planner.js'

/** Goal creation commits first. AI failure must not cause another goal POST or automatic confirmation. */
export async function createGoalWithPlan(input, { create = createGoal, generate = generatePlanDraft, onCreated = () => {} } = {}) {
  const goal = await create(input)
  onCreated(goal)
  try {
    return { goal, draft: await generate(goal.id), planError: null }
  } catch (planError) {
    return { goal, draft: null, planError }
  }
}

export function firstPlannedDate(items) {
  return items.map(item => item.scheduledDate).filter(day => typeof day === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(day)).sort()[0]
}
