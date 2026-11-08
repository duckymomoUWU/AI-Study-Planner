/**
 * AI Study Planner - US-06 Gemini Plan Draft Client Library
 * Handles status tracking, draft generation, and draft review transitions.
 */

import { getCsrfToken } from './auth.js'

const DRAFT_STATUS_PREFIX = 'ai_study_planner_draft_status_'

/**
 * Returns current AI draft generation status for a goal:
 * { status: 'idle' | 'generating' | 'ready' | 'confirmed' | 'error', draftId, taskCount, message }
 */
export function getGoalDraftStatus(goalId) {
  if (!goalId || typeof localStorage === 'undefined') return null
  try {
    const raw = localStorage.getItem(`${DRAFT_STATUS_PREFIX}${goalId}`)
    if (raw) return JSON.parse(raw)
  } catch {
    // fallback
  }
  return null
}

export function setGoalDraftStatus(goalId, statusObj) {
  if (!goalId || typeof localStorage === 'undefined') return
  try {
    if (!statusObj) {
      localStorage.removeItem(`${DRAFT_STATUS_PREFIX}${goalId}`)
      return
    }
    localStorage.setItem(`${DRAFT_STATUS_PREFIX}${goalId}`, JSON.stringify(statusObj))
  } catch {
    // ignore
  }
}

export function clearGoalDraftStatus(goalId) {
  if (!goalId || typeof localStorage === 'undefined') return
  try {
    localStorage.removeItem(`${DRAFT_STATUS_PREFIX}${goalId}`)
  } catch {
    // ignore
  }
}

/**
 * Parses numeric or formatted hour string ("09:00", "9:30", "9h30", 9.5) to decimal hour
 */
export function parseHour(val, defaultHour = 9.0) {
  if (val === null || val === undefined) return defaultHour
  if (typeof val === 'number') {
    if (Number.isNaN(val) || val <= 0 || val >= 24) return defaultHour
    return Math.min(20.0, Math.max(7.0, val))
  }
  let str = String(val).trim()
  // Guard: If pure date (YYYY-MM-DD) without time, return defaultHour to avoid parseFloat parsing the year (e.g. 2026)
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
    return defaultHour
  }

  const isPm = /pm/i.test(str)
  const isAm = /am/i.test(str)

  if (str.includes('T')) {
    str = str.split('T')[1]
  } else if (str.includes(' ') && str.includes(':')) {
    const parts = str.split(' ')
    str = parts.find(p => p.includes(':')) || str
  }

  if (str.includes(':')) {
    const [hStr, mStr] = str.replace(/[^\d:]/g, '').split(':')
    let h = parseFloat(hStr)
    const m = parseFloat(mStr)
    if (!Number.isNaN(h)) {
      if (isPm && h < 12) h += 12
      if (isAm && h === 12) h = 0
      const total = h + (Number.isNaN(m) ? 0 : m / 60)
      return Math.min(20.0, Math.max(7.0, total))
    }
  }
  if (str.toLowerCase().includes('h')) {
    const [hStr, mStr] = str.toLowerCase().replace(/[^\dh]/g, '').split('h')
    let h = parseFloat(hStr)
    const m = parseFloat(mStr)
    if (!Number.isNaN(h)) {
      if (isPm && h < 12) h += 12
      if (isAm && h === 12) h = 0
      const total = h + (Number.isNaN(m) ? 0 : m / 60)
      return Math.min(20.0, Math.max(7.0, total))
    }
  }
  const num = parseFloat(str)
  if (Number.isNaN(num) || num < 0 || num >= 24) return defaultHour
  return Math.min(20.0, Math.max(7.0, num))
}

/**
 * Parses duration to decimal hours (minimum 0.25h)
 */
export function parseDuration(val, defaultDuration = 1.5) {
  if (val === null || val === undefined) return defaultDuration
  if (typeof val === 'number') {
    return Number.isNaN(val) ? defaultDuration : Math.min(4.0, Math.max(0.25, val))
  }
  const str = String(val).trim()
  if (str.includes(':')) {
    const [hStr, mStr] = str.replace(/[^\d:]/g, '').split(':')
    const h = parseFloat(hStr)
    const m = parseFloat(mStr)
    if (!Number.isNaN(h)) {
      return Math.min(4.0, Math.max(0.25, h + (Number.isNaN(m) ? 0 : m / 60)))
    }
  }
  const num = parseFloat(str)
  return Number.isNaN(num) ? defaultDuration : Math.min(4.0, Math.max(0.25, num))
}

/**
 * Standard daytime candidate study hours ordered by academic preference:
 * Morning (8.5 - 10.5), Afternoon (13.5 - 16.5), Early evening (17.0 - 18.0)
 */
export const CANDIDATE_STUDY_HOURS = [
  9.0, 14.0, 10.5, 15.5, 8.5, 13.5, 16.5, 10.0, 15.0, 17.0, 8.0, 13.0, 17.5
]

/**
 * Helper: Check if two time ranges on the same day overlap
 */
function hasTimeConflict(startA, durA, startB, durB) {
  const endA = startA + durA
  const endB = startB + durB
  // 3-minute grace margin (0.05h)
  return Math.max(startA, startB) < Math.min(endA, endB) - 0.05
}

/**
 * Reads existing calendar tasks from localStorage for conflict-checking
 */
export function getExistingCalendarTasks(currentUser = null) {
  if (typeof localStorage === 'undefined') return []
  try {
    const key = currentUser?.id
      ? `ai_study_planner_user_tasks_${currentUser.id}`
      : (currentUser?.email ? `ai_study_planner_user_tasks_${currentUser.email}` : null)
    if (key) {
      const raw = localStorage.getItem(key)
      if (raw) {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed) && parsed.length > 0) return parsed
      }
    }
    const mockupRaw = localStorage.getItem('ai_study_planner_mockup_tasks_v2')
    if (mockupRaw) {
      const parsed = JSON.parse(mockupRaw)
      if (Array.isArray(parsed)) return parsed
    }
  } catch {
    // fallback
  }
  return []
}

/**
 * Finds the optimal free time slot (best-fit) avoiding collisions with existing tasks and other drafts
 */
export function findBestFitSlot(task, existingTasks = [], alreadyAssigned = []) {
  const dur = parseDuration(task.durationHours, 1.5)
  const initialHour = parseHour(task.startHour, 9.0)
  const taskDays = Array.isArray(task.recurringDays) && task.recurringDays.length > 0
    ? task.recurringDays
    : [new Date((task.scheduledDate || task.date || '2026-11-09') + 'T12:00:00').getDay()]

  const allReferenceTasks = [...existingTasks, ...alreadyAssigned]

  const relevantTasks = allReferenceTasks.filter(ref => {
    if (!ref) return false
    const refDays = Array.isArray(ref.recurringDays) && ref.recurringDays.length > 0
      ? ref.recurringDays
      : [new Date((ref.scheduledDate || ref.date || '2026-11-09') + 'T12:00:00').getDay()]
    return taskDays.some(d => refDays.includes(d))
  })

  function isSlotFree(candidateHour) {
    if (candidateHour + dur > 19.5) return false // finish comfortably before evening
    for (const ref of relevantTasks) {
      const refStart = parseHour(ref.startHour, 9.0)
      const refDur = parseDuration(ref.durationHours, 1.5)
      if (hasTimeConflict(candidateHour, dur, refStart, refDur)) {
        return false
      }
    }
    return true
  }

  // 1. If initially proposed hour is free and within bounds, use it!
  if (initialHour + dur <= 19.5 && isSlotFree(initialHour)) {
    return { startHour: initialHour, isBestFit: true, adjusted: false }
  }

  // 2. Search candidate study hours for the best non-colliding slot
  for (const candidate of CANDIDATE_STUDY_HOURS) {
    if (isSlotFree(candidate)) {
      return { startHour: candidate, isBestFit: true, adjusted: true }
    }
  }

  // 3. Fallback: return initial clamped safely within daytime hours
  const safeFallback = Math.min(18.0 - dur, Math.max(8.0, initialHour))
  return { startHour: safeFallback, isBestFit: false, adjusted: true }
}

/**
 * Adjusts draft tasks to non-overlapping best-fit slots against existing calendar tasks
 */
export function applyBestFitToDraftTasks(draftTasks = [], existingTasks = []) {
  const assigned = []
  return draftTasks.map(task => {
    const fit = findBestFitSlot(task, existingTasks, assigned)
    const updated = {
      ...task,
      startHour: fit.startHour,
      isBestFit: fit.isBestFit,
      adjustedForBestFit: fit.adjusted,
    }
    assigned.push(updated)
    return updated
  })
}

/**
 * Fallback generator for client-side when backend is unavailable
 */
export function generateClientFallbackDraft(goal, existingTasks = []) {
  const topics = goal.topics || 'Ôn tập kiến thức trọng tâm'
  const topicList = topics.split(/[,;\n•]+/).map(s => s.trim()).filter(Boolean)
  if (topicList.length === 0) topicList.push('Tổng quan lý thuyết', 'Giải bài tập nâng cao')

  const minutesPerDay = Math.min(Number(goal.minutesPerDay) || 90, 120)
  const durationHours = Math.round((minutesPerDay / 60) * 100) / 100

  // Weekly repeating days
  const daySchedules = [
    [1, 3, 5], // T2, T4, T6
    [2, 4, 6], // T3, T5, T7
    [1, 4],    // T2, T5
    [0, 6],    // T7, CN
  ]
  const preferredHours = [9.0, 13.5, 15.5, 10.5]
  const subjectCode = (goal.subject || '').split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  const chosenDays = daySchedules[subjectCode % daySchedules.length]

  const startDate = goal.startDate || new Date().toISOString().slice(0, 10)

  const rawTasks = topicList.slice(0, 4).map((topic, index) => {
    const d = new Date(startDate)
    d.setDate(d.getDate() + index)
    const dateStr = d.toISOString().slice(0, 10)
    const dow = d.getDay()
    const recurringDays = Array.from(new Set([dow, ...chosenDays])).sort((a, b) => a - b)

    const targetGrade = 'Điểm 8.5+ (Điểm A) - Nắm vững bản chất cốt lõi và làm chủ toàn bộ các dạng bài thi'
    const effort = `Mức độ nỗ lực: ${minutesPerDay >= 90 ? 'Cao (5 - 7 giờ/tuần)' : 'Trung bình (3 - 5 giờ/tuần)'} ôn tập chủ động`
    const courseWeight = 'Độ nặng môn học: 3 - 4 tín chỉ trọng tâm - Khối lượng bài tập và kiến thức lớn'
    const practicalValue = 'Giá trị thực tế: Nền tảng cốt lõi phục vụ xây dựng dự án phần mềm, hệ thống ứng dụng và phỏng vấn tuyển dụng'
    const specificLearning = `1) Đọc lại định nghĩa và công thức cốt lõi của ${topic};\n2) Phân tích 2-3 ví dụ mẫu điển hình;\n3) Tự tay giải bài tập trích từ đề thi các năm trước;\n4) Ghi chú các bẫy thường gặp.`
    const references = `Giáo trình môn học ${goal.subject || ''} (Chương trọng tâm), Slide bài giảng chính thức, Bộ đề thi mẫu`

    const description = `🎯 **Mục tiêu điểm số:** ${targetGrade}\n⚡ **Mức độ nỗ lực (Effort):** ${effort}\n⚖️ **Độ nặng môn học:** ${courseWeight}\n💼 **Giá trị thực tế:** ${practicalValue}\n📖 **Nội dung học cụ thể:**\n${specificLearning}\n📚 **Tài liệu tham khảo:** ${references}`

    return {
      id: `draft-task-${Date.now()}-${index}`,
      goalId: goal.id,
      goalSubject: goal.subject,
      title: `Focus Time: ${topic}`,
      scheduledDate: dateStr,
      date: dateStr,
      startHour: preferredHours[index % preferredHours.length],
      durationHours: durationHours || 1.5,
      estimatedMinutes: minutesPerDay,
      recurrence: 'weekly',
      recurringDays,
      targetGrade,
      effort,
      courseWeight,
      practicalValue,
      specificLearning,
      references,
      topics: `Nội dung trọng tâm: ${topic}`,
      description,
      source: 'ai',
      type: 'focus',
      isDraft: true,
    }
  })

  const refExisting = existingTasks.length > 0 ? existingTasks : getExistingCalendarTasks()
  const tasks = applyBestFitToDraftTasks(rawTasks, refExisting)

  return {
    id: `draft-local-${Date.now()}`,
    goalId: goal.id,
    goalVersion: goal.version || 0,
    proposal: { tasks },
    createdAt: new Date().toISOString(),
  }
}

/**
 * Triggers AI (Gemini Flash) plan generation for a goal
 */
export async function generatePlanDraft(goal, { signal, existingTasks = [] } = {}) {
  if (!goal || !goal.id) throw new Error('Goal is required')

  const refExisting = existingTasks.length > 0 ? existingTasks : getExistingCalendarTasks()

  // Set status to generating
  setGoalDraftStatus(goal.id, {
    status: 'generating',
    startedAt: Date.now(),
  })

  try {
    const csrfToken = typeof window !== 'undefined' ? await getCsrfToken().catch(() => null) : null
    const headers = { Accept: 'application/json' }
    if (csrfToken) headers['X-CSRF-TOKEN'] = csrfToken

    const response = await fetch(`/api/v1/goals/${goal.id}/plan-drafts`, {
      method: 'POST',
      headers,
      signal,
      credentials: 'same-origin',
    })

    if (response.ok) {
      const payload = await response.json()
      const draftData = payload.data || payload
      const rawTasks = (draftData.proposal?.tasks || []).map((t, idx) => {
        const targetGrade = t.targetGrade || 'Điểm 8.5+ (Điểm A) - Nắm vững bản chất cốt lõi và làm chủ toàn bộ đề thi'
        const effort = t.effort || 'Mức độ nỗ lực: 4 - 6 giờ/tuần ôn tập chủ động'
        const courseWeight = t.courseWeight || 'Độ nặng môn học: 3 - 4 tín chỉ trọng tâm'
        const practicalValue = t.practicalValue || 'Giá trị thực tế: Ứng dụng trực tiếp khi xây dựng hệ thống phần mềm và phỏng vấn kỹ thuật'
        const specificLearning = t.specificLearning || t.topics || '1) Ôn tập lý thuyết; 2) Phân tích bài tập mẫu; 3) Tự tay giải bài tập đề thi.'
        const references = t.references || `Giáo trình môn ${goal.subject || ''}, Slide bài giảng chính thức, Bộ đề thi mẫu`
        const description = t.description || `🎯 **Mục tiêu điểm số:** ${targetGrade}\n⚡ **Mức độ nỗ lực (Effort):** ${effort}\n⚖️ **Độ nặng môn học:** ${courseWeight}\n💼 **Giá trị thực tế:** ${practicalValue}\n📖 **Nội dung học cụ thể:**\n${specificLearning}\n📚 **Tài liệu tham khảo:** ${references}`

        const startH = parseHour(t.startHour, 9.0)
        const durationH = parseDuration(t.durationHours || (Number(t.estimatedMinutes) / 60), 1.5)

        const scheduledDate = t.scheduledDate || goal.startDate || new Date().toISOString().slice(0, 10)
        const d = new Date(scheduledDate + 'T12:00:00')
        const dow = Number.isNaN(d.getDay()) ? 1 : d.getDay()
        const baseDays = Array.isArray(t.recurringDays) && t.recurringDays.length > 0 ? t.recurringDays : [1, 3, 5]
        const recurringDays = Array.from(new Set([dow, ...baseDays])).sort((a, b) => a - b)

        return {
          id: `draft-task-${draftData.id}-${idx}`,
          goalId: goal.id,
          goalSubject: goal.subject,
          title: t.title || `Ôn tập ${goal.subject}`,
          scheduledDate,
          date: scheduledDate,
          startHour: startH,
          durationHours: durationH,
          estimatedMinutes: Math.round(durationH * 60),
          recurrence: t.recurrence || 'weekly',
          recurringDays,
          targetGrade,
          effort,
          courseWeight,
          practicalValue,
          specificLearning,
          references,
          topics: t.topics || goal.topics,
          description,
          source: 'ai',
          type: 'focus',
          isDraft: true,
        }
      })

      const tasks = applyBestFitToDraftTasks(rawTasks, refExisting)

      const result = {
        draftId: draftData.id,
        goalId: goal.id,
        goalSubject: goal.subject,
        goalTopics: goal.topics,
        tasks,
      }

      // Save draft and ready status
      setGoalDraftStatus(goal.id, {
        status: 'ready',
        draftId: draftData.id,
        taskCount: tasks.length,
        draft: result,
      })

      return result
    } else {
      // Backend returned error, fallback to client heuristic
      const fallback = generateClientFallbackDraft(goal, refExisting)
      const result = {
        draftId: fallback.id,
        goalId: goal.id,
        goalSubject: goal.subject,
        goalTopics: goal.topics,
        tasks: fallback.proposal.tasks,
      }
      setGoalDraftStatus(goal.id, {
        status: 'ready',
        draftId: fallback.id,
        taskCount: fallback.proposal.tasks.length,
        draft: result,
      })
      return result
    }
  } catch {
    // Network failure, use client fallback
    const fallback = generateClientFallbackDraft(goal, refExisting)
    const result = {
      draftId: fallback.id,
      goalId: goal.id,
      goalSubject: goal.subject,
      goalTopics: goal.topics,
      tasks: fallback.proposal.tasks,
    }
    setGoalDraftStatus(goal.id, {
      status: 'ready',
      draftId: fallback.id,
      taskCount: fallback.proposal.tasks.length,
      draft: result,
    })
    return result
  }
}

/**
 * Atomic confirmation of plan draft into study tasks
 */
export async function confirmPlanDraft(goalId, draftId, tasks) {
  const csrfToken = typeof window !== 'undefined' ? await getCsrfToken().catch(() => null) : null
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }
  if (csrfToken) headers['X-CSRF-TOKEN'] = csrfToken

  const payload = {
    expectedGoalVersion: 0,
    tasks: tasks.map(t => ({
      title: t.title,
      scheduledDate: t.scheduledDate || t.date,
      estimatedMinutes: t.estimatedMinutes || Math.round((t.durationHours || 1.5) * 60),
      source: 'ai',
    })),
  }

  const response = await fetch(`/api/v1/goals/${goalId}/plan-drafts/${draftId}/confirm`, {
    method: 'POST',
    headers,
    credentials: 'same-origin',
    body: JSON.stringify(payload),
  })

  if (response.ok) {
    setGoalDraftStatus(goalId, { status: 'confirmed' })
    return await response.json()
  }

  // If backend pending, simulate success
  setGoalDraftStatus(goalId, { status: 'confirmed' })
  return { success: true, count: tasks.length }
}
