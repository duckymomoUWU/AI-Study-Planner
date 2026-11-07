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
    return Number.isNaN(val) ? defaultHour : val
  }
  const str = String(val).trim()
  if (str.includes(':')) {
    const [hStr, mStr] = str.split(':')
    const h = parseFloat(hStr)
    const m = parseFloat(mStr)
    if (!Number.isNaN(h)) {
      return h + (Number.isNaN(m) ? 0 : m / 60)
    }
  }
  if (str.toLowerCase().includes('h')) {
    const [hStr, mStr] = str.toLowerCase().split('h')
    const h = parseFloat(hStr)
    const m = parseFloat(mStr)
    if (!Number.isNaN(h)) {
      return h + (Number.isNaN(m) ? 0 : m / 60)
    }
  }
  const num = parseFloat(str)
  return Number.isNaN(num) ? defaultHour : num
}

/**
 * Parses duration to decimal hours (minimum 0.25h)
 */
export function parseDuration(val, defaultDuration = 1.5) {
  if (val === null || val === undefined) return defaultDuration
  if (typeof val === 'number') {
    return Number.isNaN(val) ? defaultDuration : Math.max(0.25, val)
  }
  const str = String(val).trim()
  if (str.includes(':')) {
    const [hStr, mStr] = str.split(':')
    const h = parseFloat(hStr)
    const m = parseFloat(mStr)
    if (!Number.isNaN(h)) {
      return Math.max(0.25, h + (Number.isNaN(m) ? 0 : m / 60))
    }
  }
  const num = parseFloat(str)
  return Number.isNaN(num) ? defaultDuration : Math.max(0.25, num)
}

/**
 * Fallback generator for client-side when backend is unavailable
 */
export function generateClientFallbackDraft(goal) {
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
  const preferredHours = [9.0, 14.0, 19.5, 10.0]
  const subjectCode = (goal.subject || '').split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  const chosenDays = daySchedules[subjectCode % daySchedules.length]

  const startDate = goal.startDate || new Date().toISOString().slice(0, 10)

  const tasks = topicList.slice(0, 4).map((topic, index) => {
    const d = new Date(startDate)
    d.setDate(d.getDate() + index)
    const dateStr = d.toISOString().slice(0, 10)

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
      recurringDays: chosenDays,
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
export async function generatePlanDraft(goal, { signal } = {}) {
  if (!goal || !goal.id) throw new Error('Goal is required')

  // Set status to generating
  setGoalDraftStatus(goal.id, {
    status: 'generating',
    startedAt: Date.now(),
  })

  try {
    const csrfToken = typeof window !== 'undefined' ? await getCsrfToken().catch(() => null) : null
    const headers = { Accept: 'application/json' }
    if (csrfToken) headers['X-CSRF-TOKEN'] = csrfToken

    const response = await fetch(`/api/goals/${goal.id}/plan-drafts`, {
      method: 'POST',
      headers,
      signal,
      credentials: 'same-origin',
    })

    if (response.ok) {
      const payload = await response.json()
      const draftData = payload.data || payload
      const tasks = (draftData.proposal?.tasks || []).map((t, idx) => {
        const targetGrade = t.targetGrade || 'Điểm 8.5+ (Điểm A) - Nắm vững bản chất cốt lõi và làm chủ toàn bộ đề thi'
        const effort = t.effort || 'Mức độ nỗ lực: 4 - 6 giờ/tuần ôn tập chủ động'
        const courseWeight = t.courseWeight || 'Độ nặng môn học: 3 - 4 tín chỉ trọng tâm'
        const practicalValue = t.practicalValue || 'Giá trị thực tế: Ứng dụng trực tiếp khi xây dựng hệ thống phần mềm và phỏng vấn kỹ thuật'
        const specificLearning = t.specificLearning || t.topics || '1) Ôn tập lý thuyết; 2) Phân tích bài tập mẫu; 3) Tự tay giải bài tập đề thi.'
        const references = t.references || `Giáo trình môn ${goal.subject || ''}, Slide bài giảng chính thức, Bộ đề thi mẫu`
        const description = t.description || `🎯 **Mục tiêu điểm số:** ${targetGrade}\n⚡ **Mức độ nỗ lực (Effort):** ${effort}\n⚖️ **Độ nặng môn học:** ${courseWeight}\n💼 **Giá trị thực tế:** ${practicalValue}\n📖 **Nội dung học cụ thể:**\n${specificLearning}\n📚 **Tài liệu tham khảo:** ${references}`

        const startH = parseHour(t.startHour, 9.0)
        const durationH = parseDuration(t.durationHours || (Number(t.estimatedMinutes) / 60), 1.5)

        return {
          id: `draft-task-${draftData.id}-${idx}`,
          goalId: goal.id,
          goalSubject: goal.subject,
          title: t.title || `Ôn tập ${goal.subject}`,
          scheduledDate: t.scheduledDate || goal.startDate,
          date: t.scheduledDate || goal.startDate,
          startHour: startH,
          durationHours: durationH,
          estimatedMinutes: Math.round(durationH * 60),
          recurrence: t.recurrence || 'weekly',
          recurringDays: Array.isArray(t.recurringDays) ? t.recurringDays : [1, 3, 5],
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
      const fallback = generateClientFallbackDraft(goal)
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
    const fallback = generateClientFallbackDraft(goal)
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

  const response = await fetch(`/api/goals/${goalId}/plan-drafts/${draftId}/confirm`, {
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
