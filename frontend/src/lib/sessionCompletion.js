import { getTask, setTaskCompleted } from './tasks.js'

export async function loadSessionTask(session, options = {}) {
  if (session?.status !== 'COMPLETED' || !session.goalId || !session.taskId) return null
  return getTask(session.goalId, session.taskId, options)
}

// Called only after the user's explicit choice, never by the Timer FINISH action.
export async function completeSessionTask(session, options = {}) {
  const task = await loadSessionTask(session, options)
  if (!task || task.completed) return task
  return setTaskCompleted(session.goalId, session.taskId, true, options)
}
