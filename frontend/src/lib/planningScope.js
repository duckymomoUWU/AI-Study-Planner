export function topicsFromText(text) {
  return [...new Set(text.split(/[\n;,]+/).map(value => value.trim()).filter(Boolean))]
    .map((title, i) => ({ id: `manual-${i + 1}`, title }))
}

export function planningScopeForForm(form, selectedTopics = [], dailyMinutes = {}) {
  return {
    selectedTopics: selectedTopics.length ? selectedTopics : topicsFromText(form.topics),
    dailyMinutes: Object.fromEntries(Object.entries(dailyMinutes).filter(([day]) => day >= form.startDate && day < form.examDate)),
  }
}

export function savedPlanningScope(goal) {
  return goal.planningScope?.selectedTopics ? goal.planningScope : { selectedTopics: topicsFromText(goal.topics), dailyMinutes: {} }
}

export function applySyllabusFields(previous, data) {
  return { ...previous, subject: data.subject, topics: data.topics,
    ...(data.examDate ? { examDate: data.examDate } : {}) }
}

export function studyDates(start, exam) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(exam) || start >= exam) return []
  const days = []
  const date = new Date(`${start}T12:00:00Z`)
  while (date.toISOString().slice(0, 10) < exam && days.length < 90) {
    days.push(date.toISOString().slice(0, 10))
    date.setUTCDate(date.getUTCDate() + 1)
  }
  return days
}

export function selectedOutlineTopics(chapters, selected) {
  return chapters.flatMap(chapter => chapter.topics.filter(topic => selected.includes(topic.id)).map(topic => ({
    id: topic.id, title: topic.title, chapter: chapter.title, sources: topic.sources,
    ...(topic.priority ? { priority: topic.priority } : {}),
  })))
}

/** Async callers invalidate on input changes/unmount; transports may ignore AbortSignal. */
export function requestRevision() {
  let revision = 0
  return { next: () => ++revision, isCurrent: value => value === revision }
}
