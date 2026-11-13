export function overviewSummary(goals, tasks, today) {
  const upcoming = goals.filter(goal => goal.examDate >= today)
    .toSorted((a, b) => a.examDate.localeCompare(b.examDate) || a.subject.localeCompare(b.subject))
  const completed = tasks.filter(task => task.completed).length
  return {
    upcoming,
    completed,
    total: tasks.length,
    remaining: tasks.length - completed,
    remainingMinutes: tasks.filter(task => !task.completed).reduce((sum, task) => sum + task.estimatedMinutes, 0),
    progress: tasks.length ? Math.round(completed * 100 / tasks.length) : 0,
  }
}

export function daysToExam(today, examDate) {
  return Math.round((Date.parse(`${examDate}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86400000)
}
