import { listTasksByDate } from './tasks.js'

export function offsetDay(day, offset) {
  const date = new Date(`${day}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + offset)
  return date.toISOString().slice(0, 10)
}

export function weekDays(day) {
  const weekday = new Date(`${day}T12:00:00Z`).getUTCDay()
  const monday = offsetDay(day, -((weekday + 6) % 7))
  return Array.from({ length: 7 }, (_, i) => offsetDay(monday, i))
}

export async function loadStudyWeek(day, { signal, load = listTasksByDate } = {}) {
  return Promise.all(weekDays(day).map(date => load(date, { signal })))
}

export function visibleStudyDays(days, goalId = '') {
  return days.map(day => {
    const items = day.items.filter(task => !goalId || task.goalId === goalId)
    return { date: day.date, items, minutes: items.reduce((sum, task) => sum + task.estimatedMinutes, 0),
      completed: items.filter(task => task.completed).length }
  })
}
