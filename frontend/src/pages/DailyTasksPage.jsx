import { useEffect, useState } from 'react'
import { listGoals } from '../lib/goals.js'
import { confirmPlanDraft, parseHour, parseDuration } from '../lib/ai-planner.js'
import './DailyTasksPage.css'

// Helper: Format date YYYY-MM-DD
function toDateKey(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function addDays(baseDate, days) {
  const result = new Date(baseDate)
  result.setDate(result.getDate() + days)
  return result
}

const STORAGE_KEY_MOCKUP = 'ai_study_planner_calendar_tasks_mockup_v5'

// Exact reference mockup calendar tasks matching May 18-20, 2026 (Mon - Wed)
function getMockupCalendarTasks() {
  return [
    // ==========================================
    // MON 18 MAY 2026
    // ==========================================
    {
      id: 'mock-mon-1',
      title: 'Học trên lớp: Kiến trúc máy tính & Vi xử lý',
      overviewLabel: '', // Clean gray block in overview
      subject: 'Khoa học máy tính',
      type: 'other', // Gray block
      date: '2026-05-18',
      startHour: 9.0,
      durationHours: 1.85, // 9:00 - 10:51
      completed: false,
      topics: 'Cấu trúc vi xử lý, pipeline instruction, bộ nhớ đệm cache L1/L2',
      location: 'Giảng đường H2-301',
      recurrence: 'weekly',
      recurringDays: [1], // Mỗi Thứ Hai hàng tuần
    },
    {
      id: 'mock-mon-2',
      title: 'Ăn uống & Sinh hoạt cá nhân',
      overviewLabel: 'Sinh hoạt cá nhân',
      subject: 'Sinh hoạt cá nhân',
      type: 'routine', // Warm yellow block (Personal routine)
      date: '2026-05-18',
      startHour: 11.0,
      durationHours: 1.0, // 11:00 - 12:00
      completed: true,
      topics: 'Ăn trưa, vệ sinh cá nhân và nghỉ ngơi nạp lại năng lượng',
      recurrence: 'none',
      recurringDays: [],
    },
    {
      id: 'mock-mon-3',
      title: 'Tự học ngoại ngữ & Đọc tin tức công nghệ',
      overviewLabel: '', // Clean gray block
      subject: 'Ngoại ngữ & Kỹ năng',
      type: 'other',
      date: '2026-05-18',
      startHour: 12.15,
      durationHours: 0.75, // 12:09 - 12:54
      completed: false,
      topics: 'Luyện 20 từ vựng chuyên ngành IT qua Anki flashcards',
    },
    {
      id: 'mock-mon-4',
      title: 'Focus Time: Ôn tập Tích phân đường loại 1 & 2',
      overviewLabel: 'Focus Time',
      subject: 'Giải tích 2',
      type: 'focus', // Vibrant green block (Web app generated)
      date: '2026-05-18',
      startHour: 13.0,
      durationHours: 2.6, // 13:00 - 15:36
      completed: false,
      topics: 'Định lý Green, công thức Stokes, giải 5 bài tập mẫu đề thi cuối kỳ',
      targetGrade: 'Điểm 8.5+ (Điểm A) - Thành thạo kỹ năng tính tích phân đường và định lý Green',
      effort: 'Cao (5 - 6 giờ/tuần ôn tập chủ động)',
      courseWeight: '4 tín chỉ đại cương trọng tâm - Lượng bài tập lớn',
      practicalValue: 'Nền tảng cho mô hình hóa vật lý, tính công cơ học và trường vector',
      specificLearning: '1) Ôn tập định nghĩa và tính chất tích phân đường loại 1, loại 2;\n2) Ứng dụng công thức Green chuyển tích phân đường về tích phân kép;\n3) Giải 5 bài toán mẫu trích từ đề thi cuối kỳ năm 2024-2025;\n4) Rà soát các lỗi sai dấu và xác định hướng đường cong kín.',
      references: 'Giáo trình Giải tích 2 (Chương 4), Bài giảng điện tử Bộ môn Toán, Bộ đề thi mẫu cuối kỳ',
      description: '🎯 **Mục tiêu điểm số:** Điểm 8.5+ (Điểm A)\n⚡ **Mức độ nỗ lực:** Cao (5-6h/tuần)\n⚖️ **Độ nặng môn học:** 4 tín chỉ đại cương trọng tâm\n💼 **Giá trị thực tế:** Nền tảng mô hình hóa vật lý\n📖 **Nội dung học cụ thể:** Định lý Green, công thức Stokes, giải 5 bài tập mẫu\n📚 **Tài liệu tham khảo:** Giáo trình Giải tích 2 Chương 4',
      source: 'ai',
    },
    {
      id: 'mock-mon-5',
      title: 'Chạy bộ & Rèn luyện thể lực',
      overviewLabel: '', // Clean gray block
      subject: 'Thể thao & Sức khỏe',
      type: 'other',
      date: '2026-05-18',
      startHour: 16.05,
      durationHours: 0.85, // 16:03 - 16:54
      completed: false,
      topics: 'Chạy cự ly 3km quanh sân vận động trường',
    },

    // ==========================================
    // TUE 19 MAY 2026
    // ==========================================
    {
      id: 'mock-tue-1',
      title: 'Làm việc nhóm & Chuẩn bị tài liệu môn học',
      overviewLabel: '', // Clean gray block
      subject: 'Kỹ năng làm việc nhóm',
      type: 'other',
      date: '2026-05-19',
      startHour: 9.0,
      durationHours: 0.85, // 9:00 - 9:51
      completed: true,
      topics: 'Rà soát phân công công việc tuần 4 của nhóm',
    },
    {
      id: 'mock-tue-2',
      title: 'Focus Time: Luyện đề SQL & Chuẩn hóa quan hệ BCNF',
      overviewLabel: 'Focus Time',
      subject: 'Cơ sở dữ liệu',
      type: 'focus', // Vibrant green block (Web app generated)
      date: '2026-05-19',
      startHour: 10.0,
      durationHours: 3.0, // 10:00 - 13:00
      completed: false,
      topics: 'Thực hành chuẩn hóa 1NF - BCNF, giải trọn vẹn đề thi giữa kỳ năm 2025',
      targetGrade: 'Điểm 9.0+ (Điểm A+) - Đạt điểm tối đa phần chuẩn hóa quan hệ và SQL nâng cao',
      effort: 'Cao (4 - 6 giờ/tuần thực hành code SQL)',
      courseWeight: '3 tín chỉ chuyên ngành cốt lõi',
      practicalValue: 'Thiết kế schema chuẩn cho mọi ứng dụng backend thực tế và phỏng vấn database',
      specificLearning: '1) Nhắc lại định nghĩa phụ thuộc hàm và bao đóng thuộc tính;\n2) Thực hành thuật toán tìm khóa và chuẩn hóa dạng chuẩn 1NF, 2NF, 3NF, BCNF;\n3) Giải trọn vẹn đề thi giữa kỳ năm 2025;\n4) Viết câu lệnh DDL/DML kiểm thử trên PostgreSQL.',
      references: 'Giáo trình Hệ quản trị CSDL, Slide bài giảng chuẩn hóa quan hệ, Bài tập SQL thực hành trên máy',
      description: '🎯 **Mục tiêu điểm số:** Điểm 9.0+ (Điểm A+)\n⚡ **Mức độ nỗ lực:** Cao (4-6h/tuần)\n⚖️ **Độ nặng môn học:** 3 tín chỉ chuyên ngành\n💼 **Giá trị thực tế:** Thiết kế schema CSDL backend\n📖 **Nội dung học cụ thể:** Chuẩn hóa BCNF, giải đề thi 2025\n📚 **Tài liệu tham khảo:** Slide Hệ CSDL và đề thi giữa kỳ',
      source: 'ai',
    },
    {
      id: 'mock-tue-3',
      title: 'Thư giãn & Cafe đầu giờ chiều',
      overviewLabel: '', // Clean gray block
      subject: 'Sinh hoạt cá nhân',
      type: 'other',
      date: '2026-05-19',
      startHour: 13.15,
      durationHours: 0.75, // 13:09 - 13:54
      completed: false,
      topics: 'Giải lao nạp năng lượng',
    },
    {
      id: 'mock-tue-4',
      title: 'Ăn uống & Sinh hoạt cá nhân',
      overviewLabel: 'Sinh hoạt cá nhân',
      subject: 'Sinh hoạt cá nhân',
      type: 'routine',
      date: '2026-05-19',
      startHour: 14.0,
      durationHours: 1.0, // 14:00 - 15:00
      completed: true,
      topics: 'Bữa trưa, vệ sinh cá nhân và thư giãn giải lao',
      recurrence: 'none',
      recurringDays: [],
    },
    {
      id: 'mock-tue-5',
      title: 'Trao đổi nhanh đồ án với bạn cùng nhóm',
      overviewLabel: '', // Clean gray block
      subject: 'Đồ án CNPM',
      type: 'other',
      date: '2026-05-19',
      startHour: 15.6,
      durationHours: 0.35, // 15:36 - 15:57
      completed: false,
      topics: 'Đồng bộ API contract giữa Frontend và Backend',
    },
    {
      id: 'mock-tue-6',
      title: 'Họp cố vấn học tập & Đăng ký môn học',
      overviewLabel: '', // Clean gray block
      subject: 'Công việc khoa viện',
      type: 'other',
      date: '2026-05-19',
      startHour: 16.05,
      durationHours: 0.85, // 16:03 - 16:54
      completed: false,
      topics: 'Nghe tư vấn lộ trình học kỳ chuyên ngành',
    },

    // ==========================================
    // WED 20 MAY 2026
    // ==========================================
    {
      id: 'mock-wed-1',
      title: 'Kiểm tra email & Lên danh sách việc cần làm',
      overviewLabel: '', // Clean gray block
      subject: 'Kế hoạch cá nhân',
      type: 'other',
      date: '2026-05-20',
      startHour: 9.0,
      durationHours: 0.5, // 9:00 - 9:30
      completed: true,
      topics: 'Sắp xếp mức độ ưu tiên các việc trong ngày',
    },
    {
      id: 'mock-wed-2',
      title: 'Thực hành Lab Hệ điều hành & Shell Linux',
      overviewLabel: '', // Clean gray block
      subject: 'Hệ điều hành',
      type: 'other',
      date: '2026-05-20',
      startHour: 9.55,
      durationHours: 2.7, // 9:33 - 12:15
      completed: false,
      topics: 'Process scheduling, IPC pipes, shared memory bài lab 3',
      location: 'Phòng thực hành C3-205',
    },
    {
      id: 'mock-wed-3',
      title: 'Ăn uống & Vệ sinh cá nhân',
      overviewLabel: 'Sinh hoạt cá nhân',
      subject: 'Sinh hoạt cá nhân',
      type: 'routine',
      date: '2026-05-20',
      startHour: 12.35,
      durationHours: 0.55, // 12:21 - 12:54
      completed: true,
      topics: 'Ăn trưa nhẹ, rửa mặt, vệ sinh cá nhân chuẩn bị cho buổi học chiều',
      recurrence: 'none',
      recurringDays: [],
    },
    {
      id: 'mock-wed-4',
      title: 'Focus Time: Ôn tập Transaction & Concurrency Control',
      overviewLabel: 'Focus Time',
      subject: 'Cơ sở dữ liệu',
      type: 'focus', // Vibrant green block (Web app generated)
      date: '2026-05-20',
      startHour: 13.05,
      durationHours: 2.0, // 13:03 - 15:03
      completed: false,
      topics: 'Khóa 2 pha 2PL, kiểm tra mức cô lập Read Committed / Serializable',
      targetGrade: 'Điểm 8.5+ (Điểm A) - Nắm chắc tính chất ACID và các mức cô lập giao dịch',
      effort: 'Trung bình - Cao (3 - 4 giờ/tuần)',
      courseWeight: '3 tín chỉ chuyên ngành',
      practicalValue: 'Xử lý tranh chấp dữ liệu khi thiết kế API ngân hàng, đặt vé, thương mại điện tử',
      specificLearning: '1) Phân tích 4 tính chất ACID (Atomicity, Consistency, Isolation, Durability);\n2) Cơ chế kiểm soát đồng thời: Khóa 2 pha 2PL, Timestamp ordering;\n3) So sánh Read Committed, Repeatable Read, Serializable;\n4) Giải bài tập xung đột Read-Write, Write-Write.',
      references: 'Giáo trình Cơ sở dữ liệu nâng cao (Chương Giao dịch & Đồng thời), Slide bài giảng Tuần 8',
      description: '🎯 **Mục tiêu điểm số:** Điểm 8.5+ (Điểm A)\n⚡ **Mức độ nỗ lực:** 3-4 giờ/tuần\n⚖️ **Độ nặng môn học:** 3 tín chỉ chuyên ngành\n💼 **Giá trị thực tế:** Tránh race condition trong hệ thống phân tán\n📖 **Nội dung học cụ thể:** ACID, khóa 2PL, bài tập xung đột\n📚 **Tài liệu tham khảo:** Slide Tuần 8 CSDL',
      source: 'ai',
    },
    {
      id: 'mock-wed-5',
      title: 'Đọc tài liệu React Hooks & Vite Build',
      overviewLabel: '', // Clean gray block
      subject: 'Công nghệ Web',
      type: 'other',
      date: '2026-05-20',
      startHour: 15.2,
      durationHours: 0.65, // 15:12 - 15:51
      completed: false,
      topics: 'Tìm hiểu useMemo, useCallback và tối ưu render cây component',
    },
    {
      id: 'mock-wed-6',
      title: 'Weekly Team Meeting',
      overviewLabel: 'Weekly Team Meeting',
      subject: 'Đồ án CNPM / Sprint Review',
      type: 'meeting', // Indigo/Blue block with green checkmark
      date: '2026-05-20',
      startHour: 16.0,
      durationHours: 0.9, // 16:00 - 16:54
      completed: true,
      topics: 'Báo cáo tiến độ Sprint 2, demo màn hình lịch học và mục tiêu với Product Owner',
      location: 'Google Meet',
      recurrence: 'weekly',
      recurringDays: [3], // Mỗi Thứ Tư hàng tuần
    },
  ]
}

// Helper: Load tasks for a specific user from localStorage (clean empty default)
function loadUserTasks(key) {
  if (!key || typeof window === 'undefined') return []
  if (key.includes('bao.tonvangia') || key.includes('f79c68f5-778a-49cf-ba2c-53ad35a49d72')) {
    const cleanMarker = 'ai_study_planner_cleaned_v3_bao.tonvangia'
    if (!localStorage.getItem(cleanMarker)) {
      try {
        localStorage.removeItem(key)
        Object.keys(localStorage).forEach(k => {
          if (k.startsWith('ai_study_planner_draft_status_') || k.includes('bao.tonvangia') || k.includes('f79c68f5-778a-49cf-ba2c-53ad35a49d72')) {
            localStorage.removeItem(k)
          }
        })
        localStorage.setItem(cleanMarker, 'true')
      } catch {
        // ignore
      }
      return []
    }
  }
  try {
    const saved = localStorage.getItem(key)
    if (saved) {
      const parsed = JSON.parse(saved)
      if (Array.isArray(parsed)) {
        return parsed.filter(t => !t.id?.startsWith('mock-') && !t.id?.startsWith('user-task-'))
      }
    }
  } catch {
    // fallback
  }
  return []
}

// Recurrence Helpers:
function computeTaskLayout(dayTasks) {
  if (!dayTasks || dayTasks.length === 0) return []
  const sorted = [...dayTasks].sort((a, b) => parseHour(a.startHour, 9) - parseHour(b.startHour, 9))
  return sorted.map((task, idx) => {
    const start = parseHour(task.startHour, 9)
    const end = start + parseDuration(task.durationHours, 1.5)
    const overlapping = sorted.filter((other, oIdx) => {
      if (oIdx === idx) return false
      const oStart = parseHour(other.startHour, 9)
      const oEnd = oStart + parseDuration(other.durationHours, 1.5)
      return Math.max(start, oStart) < Math.min(end, oEnd)
    })
    if (overlapping.length === 0) {
      return { ...task, widthStyle: 'calc(100% - 12px)', leftStyle: '6px' }
    }
    const cluster = [task, ...overlapping].sort((a, b) => {
      const diff = parseHour(a.startHour, 9) - parseHour(b.startHour, 9)
      if (diff !== 0) return diff
      return String(a.id).localeCompare(String(b.id))
    })
    const colIndex = cluster.findIndex(t => t.id === task.id) % 2
    return {
      ...task,
      widthStyle: 'calc(50% - 9px)',
      leftStyle: colIndex === 0 ? '6px' : 'calc(50% + 3px)',
    }
  })
}

function isTaskOnDate(task, dateObj) {
  const dateStr = toDateKey(dateObj)
  const dow = dateObj.getDay()
  const taskDate = task.scheduledDate || task.date || task.startDate

  // 1. If not recurring, match exact date
  if (!task.recurrence || task.recurrence === 'none') {
    return taskDate === dateStr
  }

  // 2. Date must be on or after task start date
  const taskStartDate = task.startDate || task.scheduledDate || task.date
  if (taskStartDate && dateStr < taskStartDate) {
    return false
  }

  // Check end date if specified
  if (task.endDate && dateStr > task.endDate) {
    return false
  }

  // 3. Match recurrence pattern
  if (task.recurrence === 'daily') {
    return true
  }
  if (task.recurrence === 'weekdays') {
    return dow >= 1 && dow <= 5 // Mon - Fri
  }
  if (task.recurrence === 'weekly') {
    if (Array.isArray(task.recurringDays) && task.recurringDays.length > 0) {
      return task.recurringDays.includes(dow)
    }
    if (taskStartDate) {
      const originDow = new Date(`${taskStartDate}T12:00:00`).getDay()
      return dow === originDow
    }
    return true
  }
  if (task.recurrence === 'custom') {
    return Array.isArray(task.recurringDays) && task.recurringDays.includes(dow)
  }

  return taskDate === dateStr
}

function isTaskCompletedOnDate(task, dateStr) {
  if (task.completedDates && Array.isArray(task.completedDates)) {
    return task.completedDates.includes(dateStr)
  }
  return Boolean(task.completed)
}

function getRecurrenceLabel(task) {
  if (!task.recurrence || task.recurrence === 'none') return null
  if (task.recurrence === 'daily') return 'Lặp lại mỗi ngày'
  if (task.recurrence === 'weekdays') return 'Lặp lại Thứ 2 – Thứ 6'
  if (task.recurrence === 'weekly') {
    const dow = Array.isArray(task.recurringDays) && task.recurringDays.length > 0
      ? task.recurringDays[0]
      : (task.startDate ? new Date(`${task.startDate}T12:00:00`).getDay() : 1)
    const dayNames = { 0: 'Chủ Nhật', 1: 'Thứ Hai', 2: 'Thứ Ba', 3: 'Thứ Tư', 4: 'Thứ Năm', 5: 'Thứ Sáu', 6: 'Thứ Bảy' }
    return `Lặp lại hàng tuần vào ${dayNames[dow] || 'ngày này'}`
  }
  if (task.recurrence === 'custom') {
    const dayLabels = { 1: 'T2', 2: 'T3', 3: 'T4', 4: 'T5', 5: 'T6', 6: 'T7', 0: 'CN' }
    const selected = (task.recurringDays || []).map(d => dayLabels[d]).filter(Boolean).join(', ')
    return `Lặp lại vào: ${selected}`
  }
  return 'Lặp lại định kỳ'
}

const START_HOUR = 7
const HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22]

function formatHour(h) {
  if (h === 0 || h === 24) return '12am'
  if (h === 12) return '12pm'
  if (h > 12) return `${h - 12}pm`
  return `${h}am`
}

function formatTimeRange(startVal, durationVal) {
  const startHour = parseHour(startVal, 9.0)
  const durationHours = parseDuration(durationVal, 1.5)
  const startH = Math.floor(startHour)
  const startM = Math.round((startHour - startH) * 60)
  const endTotal = startHour + durationHours
  const endH = Math.floor(endTotal)
  const endM = Math.round((endTotal - endH) * 60)

  const fmt = (h, m) => {
    const period = h >= 12 && h < 24 ? 'pm' : 'am'
    const displayH = h > 12 ? h - 12 : h === 0 ? 12 : h
    const displayM = m > 0 ? `:${String(m).padStart(2, '0')}` : ''
    return `${displayH}${displayM}${period}`
  }

  return `${fmt(startH, startM)} - ${fmt(endH, endM)} (${Math.round(durationHours * 60)} phút)`
}

export default function DailyTasksPage({
  currentUser,
  onBack,
  onNavigateAuth,
  reviewDraft,
  onFinishReview,
}) {
  const isReviewMode = Boolean(reviewDraft)
  const [draftTasks, setDraftTasks] = useState(() => reviewDraft?.tasks || [])
  const [prevDraft, setPrevDraft] = useState(reviewDraft)
  if (reviewDraft !== prevDraft) {
    setPrevDraft(reviewDraft)
    setDraftTasks(reviewDraft?.tasks || [])
  }

  const [draggingTaskId, setDraggingTaskId] = useState(null)
  const [dragOverSlot, setDragOverSlot] = useState(null)
  const [isConfirming, setIsConfirming] = useState(false)
  const [reviewFeedback, setReviewFeedback] = useState('')

  // If not logged in, view mockup; in review mode, always view real schedule with draft overlaid
  const [userMockupOverride, setUserMockupOverride] = useState(false)
  const isViewingMockup = !isReviewMode && (!currentUser || userMockupOverride)

  // Separate date state for mockup vs real schedule
  const [mockupBaseDate, setMockupBaseDate] = useState(() => new Date(2026, 4, 18, 12, 0, 0))
  const [userBaseDate, setUserBaseDate] = useState(() => {
    if (reviewDraft?.tasks?.[0]?.scheduledDate) {
      const [y, m, d] = reviewDraft.tasks[0].scheduledDate.split('-').map(Number)
      if (y && m && d) return new Date(y, m - 1, d, 12, 0, 0)
    }
    return new Date()
  })

  const baseDate = isViewingMockup ? mockupBaseDate : userBaseDate
  const setBaseDate = isViewingMockup ? setMockupBaseDate : setUserBaseDate

  const [todayKey] = useState(() => toDateKey(new Date()))
  const [viewDaysCount, setViewDaysCount] = useState(3) // 3 days (matches mockup) or 7 days

  // Separate task stores: Mockup tasks & User tasks
  const [mockupTasks, setMockupTasks] = useState(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY_MOCKUP)
      if (saved) return JSON.parse(saved)
    } catch {
      // fallback
    }
    return getMockupCalendarTasks()
  })

  const userStorageKey = currentUser?.id
    ? `ai_study_planner_user_tasks_${currentUser.id}`
    : (currentUser?.email ? `ai_study_planner_user_tasks_${currentUser.email}` : null)

  const [tasksByUser, setTasksByUser] = useState({})

  const userTasks = userStorageKey
    ? (tasksByUser[userStorageKey] ?? loadUserTasks(userStorageKey))
    : []

  function setUserTasks(updater) {
    if (!userStorageKey) return
    setTasksByUser(prev => {
      const current = prev[userStorageKey] ?? loadUserTasks(userStorageKey)
      const next = typeof updater === 'function' ? updater(current) : updater
      try {
        localStorage.setItem(userStorageKey, JSON.stringify(next))
      } catch {
        // ignore
      }
      return { ...prev, [userStorageKey]: next }
    })
  }

  // Save mockup tasks to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY_MOCKUP, JSON.stringify(mockupTasks))
    } catch {
      // ignore
    }
  }, [mockupTasks])

  function clearAllUserTasks() {
    if (!window.confirm('Bạn có chắc chắn muốn xóa toàn bộ công việc trên lịch học của bạn để làm sạch lịch?')) return
    setUserTasks([])
    setSelectedTask(null)
    try {
      if (currentUser?.id) localStorage.removeItem(`ai_study_planner_user_tasks_${currentUser.id}`)
      if (currentUser?.email) localStorage.removeItem(`ai_study_planner_user_tasks_${currentUser.email}`)
      Object.keys(localStorage).forEach(k => {
        if (k.startsWith('ai_study_planner_draft_status_')) {
          localStorage.removeItem(k)
        }
      })
    } catch {
      // ignore
    }
    setReviewFeedback('Đã làm sạch toàn bộ lịch học thành công!')
    setTimeout(() => setReviewFeedback(''), 3000)
  }

  // Selected task for detail popup
  const [selectedTask, setSelectedTask] = useState(null)
  const [isEditingTask, setIsEditingTask] = useState(false)
  const [editTaskForm, setEditTaskForm] = useState(null)
  const [copyFeedback, setCopyFeedback] = useState('')

  function handleSelectTask(task, dateStr, isCompleted) {
    setSelectedTask({
      ...task,
      activeDate: dateStr,
      isCompletedOnDate: isCompleted,
    })
    setIsEditingTask(false)
    setEditTaskForm(null)
    setCopyFeedback('')
  }

  function startEditTask(task) {
    setEditTaskForm({
      id: task.id,
      title: task.title || '',
      subject: task.subject || '',
      type: task.type || 'focus',
      date: task.activeDate || task.scheduledDate || task.date || toDateKey(new Date()),
      startHour: parseHour(task.startHour, 9.0),
      durationHours: parseDuration(task.durationHours, 1.5),
      recurrence: task.recurrence || 'none',
      recurringDays: Array.isArray(task.recurringDays) && task.recurringDays.length > 0 ? [...task.recurringDays] : [1],
      targetGrade: task.targetGrade || '',
      effort: task.effort || '',
      courseWeight: task.courseWeight || '',
      practicalValue: task.practicalValue || '',
      specificLearning: task.specificLearning || task.topics || '',
      references: task.references || '',
      description: task.description || '',
      location: task.location || '',
    })
    setIsEditingTask(true)
    setCopyFeedback('')
  }

  function saveEditTask(e) {
    e?.preventDefault()
    if (!editTaskForm || !editTaskForm.title.trim()) return

    const parsedStartHour = parseHour(editTaskForm.startHour, 9.0)
    const parsedDuration = parseDuration(editTaskForm.durationHours, 1.5)

    const updatedFields = {
      ...editTaskForm,
      title: editTaskForm.title.trim(),
      startHour: parsedStartHour,
      durationHours: parsedDuration,
      estimatedMinutes: Math.round(parsedDuration * 60),
      date: editTaskForm.date,
      scheduledDate: editTaskForm.date,
      overviewLabel: editTaskForm.title.trim(),
    }

    if (editTaskForm.targetGrade || editTaskForm.effort || editTaskForm.courseWeight || editTaskForm.practicalValue || editTaskForm.specificLearning || editTaskForm.references) {
      updatedFields.description = `🎯 **Mục tiêu điểm số:** ${editTaskForm.targetGrade}\n⚡ **Mức độ nỗ lực (Effort):** ${editTaskForm.effort}\n⚖️ **Độ nặng môn học:** ${editTaskForm.courseWeight}\n💼 **Giá trị thực tế:** ${editTaskForm.practicalValue}\n📖 **Nội dung học cụ thể:**\n${editTaskForm.specificLearning}\n📚 **Tài liệu tham khảo:** ${editTaskForm.references}`
    }

    const updater = prev => prev.map(t => (t.id === editTaskForm.id ? { ...t, ...updatedFields } : t))

    if (isReviewMode) {
      setDraftTasks(updater)
    } else if (isViewingMockup) {
      setMockupTasks(updater)
    } else {
      setUserTasks(updater)
    }

    setSelectedTask(prev => ({
      ...prev,
      ...updatedFields,
      activeDate: editTaskForm.date,
    }))
    setIsEditingTask(false)
    setCopyFeedback('✓ Đã lưu thay đổi thành công!')
    setTimeout(() => setCopyFeedback(''), 3000)
  }

  function copyTaskToClipboard(task) {
    const timeText = formatTimeRange(task.startHour, task.durationHours)
    const dateText = task.activeDate || task.scheduledDate || task.date
    const repText = getRecurrenceLabel(task)

    const lines = [
      `📌 [KẾ HOẠCH HỌC TẬP]: ${task.title}`,
      `📚 Môn học: ${task.subject || 'Chung'}`,
      `📅 Thời gian: ${dateText} · ${timeText}`,
      repText ? `🔁 Chu kỳ lặp lại: ${repText}` : null,
      task.targetGrade ? `🎯 Mục tiêu điểm số: ${task.targetGrade}` : null,
      task.effort ? `⚡ Mức độ nỗ lực: ${task.effort}` : null,
      task.courseWeight ? `⚖️ Độ nặng môn học: ${task.courseWeight}` : null,
      task.practicalValue ? `💼 Giá trị thực tế: ${task.practicalValue}` : null,
      (task.specificLearning || task.topics) ? `📖 Nội dung học cụ thể:\n${task.specificLearning || task.topics}` : null,
      task.references ? `📚 Tài liệu tham khảo:\n${task.references}` : null,
      task.description ? `📝 Mô tả & Hướng dẫn:\n${task.description}` : null,
    ].filter(Boolean)

    const fullText = lines.join('\n\n')

    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(fullText).then(() => {
        setCopyFeedback('✓ Đã sao chép nội dung vào bộ nhớ tạm!')
        setTimeout(() => setCopyFeedback(''), 3000)
      }).catch(() => {
        fallbackCopy(fullText)
      })
    } else {
      fallbackCopy(fullText)
    }
  }

  function fallbackCopy(text) {
    try {
      const el = document.createElement('textarea')
      el.value = text
      el.style.position = 'fixed'
      el.style.left = '-999999px'
      document.body.appendChild(el)
      el.focus()
      el.select()
      document.execCommand('copy')
      document.body.removeChild(el)
      setCopyFeedback('✓ Đã sao chép nội dung vào bộ nhớ tạm!')
      setTimeout(() => setCopyFeedback(''), 3000)
    } catch {
      setCopyFeedback('Không thể tự động sao chép.')
      setTimeout(() => setCopyFeedback(''), 3000)
    }
  }

  function handleDuplicateTask(task) {
    const cloneSuffix = task.title ? String(task.title.length) : '1'
    const cloneId = `${task.id}-copy-${cloneSuffix}-${task.startHour || 9}`
    const cloned = {
      ...task,
      id: cloneId,
      title: `${task.title} (Bản sao)`,
      overviewLabel: `${task.overviewLabel || task.title} (Bản sao)`,
      completed: false,
      completedDates: [],
    }

    if (isReviewMode) {
      setDraftTasks(prev => [...prev, cloned])
    } else if (isViewingMockup) {
      setMockupTasks(prev => [...prev, cloned])
    } else {
      setUserTasks(prev => [...prev, cloned])
    }

    setSelectedTask(cloned)
    setIsEditingTask(false)
    setCopyFeedback('✓ Đã nhân bản công việc thành công!')
    setTimeout(() => setCopyFeedback(''), 3000)
  }
  const [goals, setGoals] = useState([])
  const [showAddModal, setShowAddModal] = useState(false)
  const [addSlotData, setAddSlotData] = useState(() => ({
    date: !currentUser ? '2026-05-18' : toDateKey(new Date()),
    startHour: 10,
  }))

  // New task form state
  const [newForm, setNewForm] = useState({
    title: '',
    subject: '',
    type: 'focus', // 'focus', 'other', 'routine', 'meeting'
    startHour: 10,
    durationHours: 1.5,
    topics: '',
    recurrence: 'none', // 'none' | 'daily' | 'weekdays' | 'weekly' | 'custom'
    recurringDays: [1],
  })

  // Active tasks based on mode
  const activeTasks = isReviewMode
    ? [...userTasks, ...draftTasks]
    : (isViewingMockup ? mockupTasks : userTasks)

  function handleDropTask(e, targetDateStr, targetHour) {
    const taskId = e?.dataTransfer?.getData('text/plain') || draggingTaskId
    if (!taskId) return

    const dow = new Date(`${targetDateStr}T12:00:00`).getDay()
    const taskUpdater = prev => prev.map(t => {
      if (t.id === taskId) {
        setReviewFeedback(`Đã chuyển "${t.title}" sang ${targetDateStr} lúc ${formatHour(targetHour)}`)
        setTimeout(() => setReviewFeedback(''), 3000)
        return {
          ...t,
          scheduledDate: targetDateStr,
          date: targetDateStr,
          startDate: targetDateStr,
          startHour: targetHour,
          recurringDays: (t.recurrence && t.recurrence !== 'none') ? [dow] : t.recurringDays,
        }
      }
      return t
    })

    if (isReviewMode) {
      setDraftTasks(taskUpdater)
    } else if (isViewingMockup) {
      setMockupTasks(taskUpdater)
    } else {
      setUserTasks(taskUpdater)
    }

    setDragOverSlot(null)
    setDraggingTaskId(null)
  }

  async function handleConfirmDraftPlan() {
    if (!reviewDraft?.goalId || !reviewDraft?.draftId) return
    setIsConfirming(true)
    try {
      await confirmPlanDraft(reviewDraft.goalId, reviewDraft.draftId, draftTasks)
      const finalized = draftTasks.map((t, idx) => ({
        ...t,
        id: `confirmed-task-${Date.now()}-${idx}`,
        isDraft: false,
        source: 'ai',
        type: 'focus',
      }))
      setUserTasks(prev => [...prev, ...finalized])
      setReviewFeedback('Đã thêm các công việc vào lịch học thành công!')
      if (onFinishReview) {
        onFinishReview({ success: true, goalId: reviewDraft.goalId, goalSubject: reviewDraft.goalSubject })
      }
    } catch {
      const finalized = draftTasks.map((t, idx) => ({
        ...t,
        id: `confirmed-task-${Date.now()}-${idx}`,
        isDraft: false,
        source: 'ai',
        type: 'focus',
      }))
      setUserTasks(prev => [...prev, ...finalized])
      if (onFinishReview) {
        onFinishReview({ success: true, goalId: reviewDraft.goalId, goalSubject: reviewDraft.goalSubject })
      }
    } finally {
      setIsConfirming(false)
    }
  }

  function handleCancelReview() {
    if (window.confirm('Bạn có chắc muốn hủy xem bản nháp này và quay lại mục tiêu?')) {
      if (onFinishReview) {
        onFinishReview({ success: false, goalId: reviewDraft?.goalId })
      }
    }
  }

  // Load goals from API/storage for selector
  useEffect(() => {
    listGoals().then(res => setGoals(res)).catch(() => {})
  }, [])

  // Calculate visible columns (dates)
  const visibleDates = []
  for (let i = 0; i < viewDaysCount; i++) {
    visibleDates.push(addDays(baseDate, i))
  }

  // Header month-year title: in Mockup mode format as "May 2026", else "Tháng M YYYY"
  const monthYearTitle = isViewingMockup
    ? new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(baseDate)
    : new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(baseDate)

  function navigateDays(offset) {
    setBaseDate(prev => addDays(prev, offset))
  }

  function handleDateJump(dateStr) {
    if (!dateStr) return
    const [y, m, d] = dateStr.split('-').map(Number)
    if (y && m && d) {
      setBaseDate(new Date(y, m - 1, d, 12, 0, 0))
    }
  }

  function openDatePicker() {
    const el = document.getElementById('cal-jump-date-input')
    if (el?.showPicker) {
      try {
        el.showPicker()
      } catch {
        el.focus()
      }
    } else {
      el?.focus()
    }
  }

  function resetMockupData() {
    const fresh = getMockupCalendarTasks()
    setMockupTasks(fresh)
    setMockupBaseDate(new Date(2026, 4, 18, 12, 0, 0))
    setSelectedTask(null)
  }

  function handleSlotClick(dateStr, hour) {
    const dow = new Date(`${dateStr}T12:00:00`).getDay()
    setAddSlotData({ date: dateStr, startHour: hour })
    setNewForm(f => ({
      ...f,
      startHour: hour,
      subject: goals[0]?.subject || (isViewingMockup ? 'Giải tích 2' : ''),
      recurringDays: [dow],
    }))
    setShowAddModal(true)
  }

  function handleCreateTask(e) {
    e.preventDefault()
    if (!newForm.title.trim()) return

    const newTask = {
      id: `task-${Date.now()}`,
      title: newForm.title.trim(),
      overviewLabel: newForm.type === 'other' && isViewingMockup ? '' : newForm.title.trim(),
      subject: newForm.subject,
      type: newForm.type,
      date: addSlotData.date,
      startHour: Number(newForm.startHour),
      durationHours: Number(newForm.durationHours),
      topics: newForm.topics,
      completed: false,
      completedDates: [],
      recurrence: newForm.recurrence || 'none',
      recurringDays: newForm.recurrence !== 'none' ? newForm.recurringDays : [],
      startDate: addSlotData.date,
      source: newForm.type === 'focus' ? 'ai' : 'manual',
    }

    if (isReviewMode) {
      setDraftTasks(prev => [...prev, { ...newTask, isDraft: true }])
    } else if (isViewingMockup) {
      setMockupTasks(prev => [...prev, newTask])
    } else {
      setUserTasks(prev => [...prev, newTask])
    }

    setShowAddModal(false)
    setNewForm({
      title: '',
      subject: goals[0]?.subject || (isViewingMockup ? 'Giải tích 2' : ''),
      type: 'focus',
      startHour: 10,
      durationHours: 1.5,
      topics: '',
      recurrence: 'none',
      recurringDays: [1],
    })
  }

  function toggleTaskComplete(taskId, targetDate) {
    const updater = prev => prev.map(t => {
      if (t.id === taskId) {
        if (t.recurrence && t.recurrence !== 'none') {
          const completedDates = Array.isArray(t.completedDates) ? [...t.completedDates] : []
          const dateKey = targetDate || selectedTask?.activeDate || t.date
          const idx = completedDates.indexOf(dateKey)
          let isNowCompleted = false
          if (idx !== -1) {
            completedDates.splice(idx, 1)
            isNowCompleted = false
          } else {
            completedDates.push(dateKey)
            isNowCompleted = true
          }
          const updated = {
            ...t,
            completedDates,
            completed: isNowCompleted,
          }
          if (selectedTask?.id === taskId) {
            setSelectedTask({
              ...updated,
              activeDate: dateKey,
              isCompletedOnDate: isNowCompleted,
            })
          }
          return updated
        } else {
          const updated = { ...t, completed: !t.completed }
          if (selectedTask?.id === taskId) {
            setSelectedTask(updated)
          }
          return updated
        }
      }
      return t
    })

    if (isReviewMode) {
      setDraftTasks(updater)
    } else if (isViewingMockup) {
      setMockupTasks(updater)
    } else {
      setUserTasks(updater)
    }
  }

  function deleteTask(taskId) {
    if (!window.confirm('Bạn có chắc muốn xóa công việc này khỏi lịch học?')) return
    const filterer = prev => prev.filter(t => t.id !== taskId)
    if (isReviewMode) {
      setDraftTasks(filterer)
    } else if (isViewingMockup) {
      setMockupTasks(filterer)
    } else {
      setUserTasks(filterer)
    }
    setSelectedTask(null)
  }

  return (
    <div className="cal-container" aria-labelledby="cal-title">
      {/* Review Mode Banner (US-06 AI Gemini Flash) */}
      {isReviewMode && (
        <div className="cal-review-banner">
          <div className="cal-review-banner-header">
            <div className="cal-review-title">
              <span className="cal-review-badge">✨ BẢN NHÁP AI (GEMINI FLASH)</span>
              <h3>Lịch ôn tập đề xuất cho môn: {reviewDraft.goalSubject || 'Mục tiêu'}</h3>
            </div>
            <div className="cal-review-actions">
              <button
                type="button"
                className="button button--secondary button--sm"
                onClick={handleCancelReview}
              >
                Hủy xem nháp
              </button>
              <button
                type="button"
                className="button button--primary button--sm button--confirm-plan"
                onClick={handleConfirmDraftPlan}
                disabled={isConfirming}
              >
                {isConfirming ? 'Đang lưu vào lịch…' : '✓ Xác nhận thêm vào lịch học'}
              </button>
            </div>
          </div>
          <div className="cal-review-instructions">
            <span>💡 <strong>Hướng dẫn:</strong> Kéo thả các khối nhiệm vụ (✨ Nháp AI) sang khung giờ hoặc ngày khác để đổi thời gian. Bấm vào từng khối để chỉnh sửa chi tiết. Lịch này lặp lại hàng tuần (repetitive) trong suốt kỳ ôn thi.</span>
          </div>
          {reviewFeedback && (
            <div className="cal-review-feedback" style={{ fontSize: '12.5px', color: '#15803d', fontWeight: 600 }}>
              ✓ {reviewFeedback}
            </div>
          )}
        </div>
      )}

      {/* Mockup Banner (Shown ONLY when not logged in or explicitly viewing mockup) */}
      {!isReviewMode && !currentUser && (
        <div className="cal-mockup-banner">
          <div className="cal-mockup-banner-content">
            <span className="cal-mockup-pill">MOCKUP DEMO</span>
            <span className="cal-mockup-text">
              <strong>Lịch mẫu tháng 5/2026:</strong> Đang hiển thị bản mô phỏng giao diện chuẩn theo thiết kế. Bấm vào từng ô để xem chi tiết hoặc thêm công việc thử nghiệm.
            </span>
          </div>
          <div className="cal-mockup-banner-actions">
            <button
              type="button"
              className="button button--secondary button--sm"
              onClick={resetMockupData}
              title="Khôi phục lại các ô mẫu ban đầu của tháng 5/2026"
            >
              Đặt lại mẫu
            </button>
            {onNavigateAuth && (
              <button
                type="button"
                className="button button--primary button--sm"
                onClick={onNavigateAuth}
              >
                Đăng nhập để dùng lịch riêng →
              </button>
            )}
          </div>
        </div>
      )}

      {currentUser && userMockupOverride && (
        <div className="cal-mockup-banner">
          <div className="cal-mockup-banner-content">
            <span className="cal-mockup-pill">MÔ PHỎNG</span>
            <span className="cal-mockup-text">
              <strong>Lịch mẫu thiết kế:</strong> Bạn đang xem trước bản mẫu tháng 5/2026.
            </span>
          </div>
          <div className="cal-mockup-banner-actions">
            <button
              type="button"
              className="button button--secondary button--sm"
              onClick={() => setUserMockupOverride(false)}
            >
              Quay lại lịch của tôi
            </button>
          </div>
        </div>
      )}

      {/* Top Controls: Back, Mode Switcher & Actions */}
      <div className="cal-top-bar">
        <div className="cal-top-left">
          <button type="button" className="button button--secondary button--sm" onClick={onBack}>
            ← Tổng quan
          </button>

          {/* Color Legend Matching the Mockup Design */}
          <div className="cal-legend">
            <span className="legend-item" title="Khối màu xanh lá: Nhiệm vụ ôn tập được gen từ hệ thống">
              <span className="legend-dot legend-dot--focus" />
              <span>Focus Time (Web app)</span>
            </span>
            <span className="legend-item" title="Khối màu vàng: Sinh hoạt cá nhân như ăn uống, vệ sinh cá nhân, nghỉ ngơi">
              <span className="legend-dot legend-dot--routine" />
              <span>Sinh hoạt cá nhân (Vàng)</span>
            </span>
            <span className="legend-item" title="Khối màu tím/xanh dương: Họp nhóm hoặc sự kiện đã chốt">
              <span className="legend-dot legend-dot--meeting" />
              <span>Weekly Team Meeting</span>
            </span>
            <span className="legend-item" title="Khối màu xám: Tiết học trên lớp hoặc lịch trình cá nhân khác">
              <span className="legend-dot legend-dot--other" />
              <span>Lịch trình khác (Xám)</span>
            </span>
          </div>
        </div>

        <div className="cal-top-right">
          {/* Toggle between user schedule and mockup for logged in users */}
          {currentUser && (
            <button
              type="button"
              className="button button--secondary button--sm"
              onClick={() => {
                setUserMockupOverride(!userMockupOverride)
              }}
            >
              {isViewingMockup ? '← Về lịch của tôi' : '✦ Xem lịch mẫu thiết kế'}
            </button>
          )}

          {/* Clear personal schedule button if user has tasks */}
          {currentUser && !isViewingMockup && userTasks.length > 0 && (
            <button
              type="button"
              className="button button--secondary button--sm button--danger"
              onClick={clearAllUserTasks}
              title="Xóa toàn bộ các công việc trên lịch học của bạn để bắt đầu lại từ đầu"
            >
              🗑 Xóa lịch ({userTasks.length})
            </button>
          )}

          <div className="view-toggle-buttons">
            <button
              type="button"
              className={`button button--sm ${viewDaysCount === 3 ? 'button--primary' : 'button--secondary'}`}
              onClick={() => setViewDaysCount(3)}
            >
              3 ngày
            </button>
            <button
              type="button"
              className={`button button--sm ${viewDaysCount === 7 ? 'button--primary' : 'button--secondary'}`}
              onClick={() => setViewDaysCount(7)}
            >
              Cả tuần
            </button>
          </div>

          <button
            type="button"
            className="button button--primary button--sm"
            onClick={() => {
              setAddSlotData({
                date: isViewingMockup ? '2026-05-18' : toDateKey(new Date()),
                startHour: 10,
              })
              setShowAddModal(true)
            }}
          >
            + Thêm công việc
          </button>
        </div>
      </div>

      {/* Calendar Header with Navigation Arrows and Month Display */}
      <div className="cal-nav-header">
        <div className="cal-nav-actions">
          <button
            type="button"
            className="cal-arrow-btn"
            onClick={() => navigateDays(-viewDaysCount)}
            title="Thời gian trước"
            aria-label="Lùi ngày"
          >
            ←
          </button>
          <button
            type="button"
            className="cal-arrow-btn"
            onClick={() => navigateDays(viewDaysCount)}
            title="Thời gian sau"
            aria-label="Tiến ngày"
          >
            →
          </button>

          <button
            type="button"
            className="cal-month-title-btn"
            onClick={openDatePicker}
            title="Bấm để chọn nhanh ngày cụ thể"
            aria-label="Chọn ngày cụ thể"
          >
            <span id="cal-title">{monthYearTitle}</span>
            <span className="cal-dropdown-arrow" aria-hidden="true">▾</span>
            <input
              type="date"
              id="cal-jump-date-input"
              className="cal-hidden-date-input"
              value={toDateKey(baseDate)}
              onChange={e => handleDateJump(e.target.value)}
              tabIndex={-1}
              aria-hidden="true"
            />
          </button>

          {isViewingMockup ? (
            <button
              type="button"
              className="button button--secondary button--sm"
              onClick={() => setBaseDate(new Date(2026, 4, 18, 12, 0, 0))}
              title="Về ngày mẫu 18-20 tháng 5/2026 (Thứ 2 - Thứ 4)"
            >
              Về mốc 18/05/2026 (T2-T4)
            </button>
          ) : (
            <button
              type="button"
              className="button button--secondary button--sm"
              onClick={() => setBaseDate(new Date())}
            >
              Hôm nay
            </button>
          )}
        </div>

        <div className="cal-hint-text">
          <span>💡 Bấm vào ô bất kỳ để xem chi tiết đầy đủ, hoặc bấm vào khoảng trống để thêm task.</span>
        </div>
      </div>

      {/* Main Grid View */}
      <div className="cal-grid-card">
        <div className="cal-grid-wrapper">
          {/* Time Grid Header (Columns) */}
          <div
            className="cal-grid-header-row"
            style={{ gridTemplateColumns: `72px repeat(${visibleDates.length}, 1fr)` }}
          >
            <div className="cal-corner-cell" />
            {visibleDates.map(date => {
              const dayName = new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(date)
              const dayNum = date.getDate()
              const isToday = toDateKey(date) === todayKey
              return (
                <div
                  key={toDateKey(date)}
                  className={`cal-day-col-header ${isToday ? 'cal-day-col-header--today' : ''}`}
                >
                  <span className="cal-day-name">{dayName}</span>
                  <span className="cal-day-number">{dayNum}</span>
                </div>
              )
            })}
          </div>

          {/* Time Grid Body */}
          <div
            className="cal-grid-body"
            style={{ gridTemplateColumns: `72px repeat(${visibleDates.length}, 1fr)` }}
          >
            {/* Time labels column */}
            <div className="cal-time-col">
              {HOURS.map(hour => (
                <div key={hour} className="cal-time-cell">
                  <span>{formatHour(hour)}</span>
                </div>
              ))}
            </div>

            {/* Day columns */}
            {visibleDates.map(date => {
              const dateStr = toDateKey(date)
              const dayTasks = activeTasks.filter(t => isTaskOnDate(t, date))

              return (
                <div key={dateStr} className="cal-day-column">
                  {/* Grid hour slot backgrounds */}
                  {HOURS.map(hour => (
                    <div
                      key={hour}
                      className={`cal-hour-slot ${dragOverSlot?.date === dateStr && dragOverSlot?.hour === hour ? 'cal-time-cell--drag-over' : ''}`}
                      onClick={() => handleSlotClick(dateStr, hour)}
                      onDragOver={(e) => {
                        e.preventDefault()
                        e.dataTransfer.dropEffect = 'move'
                        setDragOverSlot({ date: dateStr, hour })
                      }}
                      onDragLeave={() => {
                        setDragOverSlot(null)
                      }}
                      onDrop={(e) => {
                        e.preventDefault()
                        handleDropTask(e, dateStr, hour)
                      }}
                      title={`Bấm để thêm task vào lúc ${formatHour(hour)}`}
                    />
                  ))}

                  {/* Render Task Blocks inside day column */}
                  {computeTaskLayout(dayTasks).map(task => {
                    const startH = parseHour(task.startHour, 9.0)
                    const durH = parseDuration(task.durationHours, 1.5)
                    const clampedStart = Math.min(22.5, Math.max(START_HOUR, startH))
                    const topOffset = Math.max(0, (clampedStart - START_HOUR) * 64)
                    const maxAllowedHeight = Math.max(28, (HOURS.length * 64) - topOffset - 4)
                    const blockHeight = Math.min(maxAllowedHeight, Math.max(28, durH * 64 - 4))
                    const isSelected = selectedTask?.id === task.id
                    const isCompleted = isTaskCompletedOnDate(task, dateStr)

                    // Styling class based on task type
                    let blockClass = 'cal-task-block--other'
                    if (task.type === 'routine' || task.type === 'lunch') {
                      blockClass = 'cal-task-block--routine'
                    } else if (task.type === 'meeting') {
                      blockClass = 'cal-task-block--meeting'
                    } else if (task.type === 'focus' || task.type === 'app') {
                      blockClass = 'cal-task-block--focus'
                    }

                    // Display label in overview mode (unclicked)
                    const displayLabel = task.overviewLabel !== undefined
                      ? task.overviewLabel
                      : (task.type === 'other' && isViewingMockup ? '' : task.title)

                    return (
                      <div
                        key={`${task.id}-${dateStr}`}
                        draggable={true}
                        onDragStart={(e) => {
                          e.dataTransfer.setData('text/plain', task.id)
                          e.dataTransfer.effectAllowed = 'move'
                          setDraggingTaskId(task.id)
                        }}
                        onDragEnd={() => {
                          setDraggingTaskId(null)
                          setDragOverSlot(null)
                        }}
                        className={`cal-task-block ${blockClass} ${task.isDraft ? 'cal-task-block--draft' : ''} ${draggingTaskId === task.id ? 'cal-task-block--dragging' : ''} ${isSelected ? 'cal-task-block--selected' : ''}`}
                        style={{
                          top: `${topOffset}px`,
                          height: `${blockHeight}px`,
                          left: task.leftStyle || '6px',
                          width: task.widthStyle || 'calc(100% - 12px)',
                        }}
                        onClick={(e) => {
                          e.stopPropagation()
                          handleSelectTask(task, dateStr, isCompleted)
                        }}
                        role="button"
                        tabIndex={0}
                        title={`${task.title}${task.recurrence && task.recurrence !== 'none' ? ` (${getRecurrenceLabel(task)})` : ''}`}
                      >
                        <div className="cal-task-content">
                          {task.isDraft && (
                            <span className="draft-pill-tag">✨ Nháp AI</span>
                          )}
                          {displayLabel ? (
                            <span className="cal-task-title">{displayLabel}</span>
                          ) : null}

                          {task.recurrence && task.recurrence !== 'none' && (
                            <span className="cal-task-repeat-badge" title={getRecurrenceLabel(task)}>🔁</span>
                          )}

                          {task.type === 'meeting' && (
                            <span className="cal-task-check-circle" title="Đã xác nhận">✓</span>
                          )}

                          {task.type !== 'meeting' && isCompleted && displayLabel && (
                            <span className="cal-task-check">✓</span>
                          )}
                        </div>

                        {/* If not in mockup or if block is tall enough, show time meta */}
                        {!isViewingMockup && blockHeight > 54 && (
                          <span className="cal-task-meta">
                            {formatTimeRange(task.startHour, task.durationHours)}
                          </span>
                        )}
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </div>
        </div>
      </div>

      {/* Task Detail Modal / Popover (Khi bấm vào một ô) */}
      {selectedTask && (
        <div className="detail-modal-overlay" onClick={() => { setSelectedTask(null); setIsEditingTask(false); }}>
          <div className="detail-modal-card" onClick={e => e.stopPropagation()}>
            <div className="detail-modal-header">
              <div className="detail-badge-group">
                <span className={`detail-type-badge badge--${selectedTask.type}`}>
                  {selectedTask.type === 'focus' || selectedTask.type === 'app'
                    ? '✦ Focus Time (AI Study Planner)'
                    : selectedTask.type === 'routine' || selectedTask.type === 'lunch'
                    ? '☕ Sinh hoạt cá nhân'
                    : selectedTask.type === 'meeting'
                    ? '👥 Cuộc họp / Nhóm'
                    : '📌 Lịch trình khác'}
                </span>
                {selectedTask.isDraft && (
                  <span className="badge badge--focus" style={{ background: '#f5f3ff', color: '#6d28d9', borderColor: '#c4b5fd' }}>
                    ✨ Bản nháp AI
                  </span>
                )}
                {selectedTask.recurrence && selectedTask.recurrence !== 'none' && (
                  <span className="detail-repeat-badge" title={getRecurrenceLabel(selectedTask)}>
                    🔁 {getRecurrenceLabel(selectedTask)}
                  </span>
                )}
                {(selectedTask.isCompletedOnDate ?? selectedTask.completed) && (
                  <span className="detail-status-badge">✓ Đã hoàn thành</span>
                )}
              </div>
              <button
                type="button"
                className="detail-close-btn"
                onClick={() => { setSelectedTask(null); setIsEditingTask(false); }}
                aria-label="Đóng popup chi tiết"
              >
                ✕
              </button>
            </div>

            {/* Quick Action Toolbar */}
            <div className="detail-toolbar">
              <div className="detail-toolbar-left">
                <button
                  type="button"
                  className="toolbar-btn"
                  onClick={() => copyTaskToClipboard(selectedTask)}
                  title="Sao chép toàn bộ nội dung công việc và phân tích học tập vào clipboard"
                >
                  <span className="toolbar-btn-icon">📋</span>
                  <span>Sao chép nội dung</span>
                </button>
                <button
                  type="button"
                  className={`toolbar-btn ${isEditingTask ? 'toolbar-btn--active' : ''}`}
                  onClick={() => {
                    if (isEditingTask) {
                      setIsEditingTask(false)
                    } else {
                      startEditTask(selectedTask)
                    }
                  }}
                  title="Chỉnh sửa thủ công tiêu đề, thời gian, môn học và các tiêu chí học tập"
                >
                  <span className="toolbar-btn-icon">✏️</span>
                  <span>{isEditingTask ? 'Hủy chỉnh sửa' : 'Chỉnh sửa thủ công'}</span>
                </button>
                <button
                  type="button"
                  className="toolbar-btn"
                  onClick={() => handleDuplicateTask(selectedTask)}
                  title="Tạo bản sao công việc này"
                >
                  <span className="toolbar-btn-icon">📑</span>
                  <span>Nhân bản</span>
                </button>
              </div>
              {copyFeedback && (
                <div className="detail-toast-feedback">
                  {copyFeedback}
                </div>
              )}
            </div>

            {/* If Edit Mode is Active, show the Full Edit Form */}
            {isEditingTask && editTaskForm ? (
              <form onSubmit={saveEditTask} className="detail-edit-form">
                <div className="form-group">
                  <label className="form-label" htmlFor="edit-task-title">
                    Tên công việc <span className="required-star">*</span>
                  </label>
                  <input
                    id="edit-task-title"
                    className="form-input"
                    required
                    value={editTaskForm.title}
                    onChange={e => setEditTaskForm(f => ({ ...f, title: e.target.value }))}
                  />
                </div>

                <div className="form-row-grid">
                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-subject">
                      Môn học / Phân loại
                    </label>
                    <input
                      id="edit-task-subject"
                      className="form-input"
                      value={editTaskForm.subject}
                      onChange={e => setEditTaskForm(f => ({ ...f, subject: e.target.value }))}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-type">
                      Loại công việc
                    </label>
                    <select
                      id="edit-task-type"
                      className="form-select"
                      value={editTaskForm.type}
                      onChange={e => setEditTaskForm(f => ({ ...f, type: e.target.value }))}
                    >
                      <option value="focus">Focus Time (AI Study Planner)</option>
                      <option value="other">Lịch trình khác (Xám)</option>
                      <option value="routine">Sinh hoạt cá nhân (Vàng)</option>
                      <option value="meeting">Họp nhóm (Meeting)</option>
                    </select>
                  </div>
                </div>

                <div className="form-row-grid">
                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-date">
                      Ngày
                    </label>
                    <input
                      id="edit-task-date"
                      type="date"
                      className="form-input"
                      value={editTaskForm.date}
                      onChange={e => setEditTaskForm(f => ({ ...f, date: e.target.value }))}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-starthour">
                      Giờ bắt đầu
                    </label>
                    <select
                      id="edit-task-starthour"
                      className="form-select"
                      value={editTaskForm.startHour}
                      onChange={e => setEditTaskForm(f => ({ ...f, startHour: Number(e.target.value) }))}
                    >
                      {HOURS.map(h => (
                        <option key={h} value={h}>{formatHour(h)} ({h}:00)</option>
                      ))}
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-duration">
                      Thời lượng
                    </label>
                    <select
                      id="edit-task-duration"
                      className="form-select"
                      value={editTaskForm.durationHours}
                      onChange={e => setEditTaskForm(f => ({ ...f, durationHours: Number(e.target.value) }))}
                    >
                      <option value={0.5}>30 phút (0.5h)</option>
                      <option value={1}>1 giờ (60 phút)</option>
                      <option value={1.5}>1.5 giờ (90 phút)</option>
                      <option value={2}>2 giờ (120 phút)</option>
                      <option value={2.5}>2.5 giờ (150 phút)</option>
                      <option value={3}>3 giờ (180 phút)</option>
                    </select>
                  </div>
                </div>

                {/* Recurrence in edit form */}
                <div className="form-group recurrence-group">
                  <div className="form-label-row">
                    <label className="form-label">
                      <span className="recurrence-label-icon">🔁</span> Lặp lại định kỳ
                    </label>
                  </div>
                  <div className="recurrence-options-grid">
                    {[
                      { key: 'none', label: 'Không lặp lại' },
                      { key: 'weekly', label: 'Hàng tuần' },
                      { key: 'weekdays', label: 'T2 – T6' },
                      { key: 'daily', label: 'Mỗi ngày' },
                      { key: 'custom', label: 'Tùy chọn…' },
                    ].map(opt => (
                      <button
                        key={opt.key}
                        type="button"
                        className={`recurrence-chip ${editTaskForm.recurrence === opt.key ? 'recurrence-chip--active' : ''}`}
                        onClick={() => {
                          const dow = new Date(`${editTaskForm.date}T12:00:00`).getDay()
                          setEditTaskForm(f => ({
                            ...f,
                            recurrence: opt.key,
                            recurringDays: opt.key === 'weekly' ? [dow] : opt.key === 'weekdays' ? [1,2,3,4,5] : opt.key === 'daily' ? [0,1,2,3,4,5,6] : f.recurringDays,
                          }))
                        }}
                      >
                        {opt.label}
                      </button>
                    ))}
                  </div>

                  {editTaskForm.recurrence === 'custom' && (
                    <div className="recurrence-days-pills">
                      {[
                        { dow: 1, label: 'T2' },
                        { dow: 2, label: 'T3' },
                        { dow: 3, label: 'T4' },
                        { dow: 4, label: 'T5' },
                        { dow: 5, label: 'T6' },
                        { dow: 6, label: 'T7' },
                        { dow: 0, label: 'CN' },
                      ].map(({ dow, label }) => {
                        const isSelected = editTaskForm.recurringDays.includes(dow)
                        return (
                          <button
                            key={dow}
                            type="button"
                            className={`day-pill ${isSelected ? 'day-pill--active' : ''}`}
                            onClick={() => {
                              setEditTaskForm(f => {
                                const days = isSelected
                                  ? f.recurringDays.filter(d => d !== dow)
                                  : [...f.recurringDays, dow]
                                return { ...f, recurringDays: days.length > 0 ? days : [dow] }
                              })
                            }}
                          >
                            {label}
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>

                {/* Academic Criteria Fields */}
                <div className="detail-edit-academic-box">
                  <div className="detail-edit-box-title">
                    <span>🎓 TIÊU CHÍ HỌC TẬP & PHÂN TÍCH CHUYÊN SÂU</span>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-targetGrade">
                      🎯 Mục tiêu điểm số (Target Grade)
                    </label>
                    <input
                      id="edit-task-targetGrade"
                      className="form-input"
                      placeholder="Ví dụ: Điểm 8.5+ (Điểm A) - Nắm vững bản chất..."
                      value={editTaskForm.targetGrade}
                      onChange={e => setEditTaskForm(f => ({ ...f, targetGrade: e.target.value }))}
                    />
                  </div>

                  <div className="form-row-grid">
                    <div className="form-group">
                      <label className="form-label" htmlFor="edit-task-effort">
                        ⚡ Mức độ nỗ lực (Effort)
                      </label>
                      <input
                        id="edit-task-effort"
                        className="form-input"
                        placeholder="Ví dụ: Cao (4-6h/tuần)"
                        value={editTaskForm.effort}
                        onChange={e => setEditTaskForm(f => ({ ...f, effort: e.target.value }))}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor="edit-task-courseWeight">
                        ⚖️ Độ nặng môn học (Course Workload)
                      </label>
                      <input
                        id="edit-task-courseWeight"
                        className="form-input"
                        placeholder="Ví dụ: 3-4 tín chỉ chuyên ngành - Nặng"
                        value={editTaskForm.courseWeight}
                        onChange={e => setEditTaskForm(f => ({ ...f, courseWeight: e.target.value }))}
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-practicalValue">
                      💼 Giá trị thực tế (Practical Value)
                    </label>
                    <input
                      id="edit-task-practicalValue"
                      className="form-input"
                      placeholder="Ví dụ: Nền tảng thiết kế backend, phỏng vấn tuyển dụng..."
                      value={editTaskForm.practicalValue}
                      onChange={e => setEditTaskForm(f => ({ ...f, practicalValue: e.target.value }))}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-specificLearning">
                      📖 Học cái gì cụ thể (Specific Learning / Roadmap)
                    </label>
                    <textarea
                      id="edit-task-specificLearning"
                      className="form-textarea"
                      rows={3}
                      placeholder="1) Lý thuyết; 2) Ví dụ mẫu; 3) Bài tập đề thi..."
                      value={editTaskForm.specificLearning}
                      onChange={e => setEditTaskForm(f => ({ ...f, specificLearning: e.target.value }))}
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="edit-task-references">
                      📚 Tài liệu tham khảo (References)
                    </label>
                    <input
                      id="edit-task-references"
                      className="form-input"
                      placeholder="Ví dụ: Giáo trình chương 3, Slide bài giảng, Bộ đề thi mẫu..."
                      value={editTaskForm.references}
                      onChange={e => setEditTaskForm(f => ({ ...f, references: e.target.value }))}
                    />
                  </div>
                </div>

                <div className="detail-actions">
                  <button
                    type="button"
                    className="button button--secondary button--sm"
                    onClick={() => setIsEditingTask(false)}
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    className="button button--primary button--sm"
                  >
                    ✓ Lưu thay đổi
                  </button>
                </div>
              </form>
            ) : (
              /* View Mode */
              <div className="detail-body">
                <h3 className="detail-title">{selectedTask.title}</h3>

                <div className="detail-info-grid">
                  <div className="detail-info-item">
                    <span className="detail-label">Môn học / Phân loại</span>
                    <span className="detail-value">{selectedTask.subject || 'Mục tiêu ôn tập'}</span>
                  </div>
                  <div className="detail-info-item">
                    <span className="detail-label">Thời gian</span>
                    <span className="detail-value">
                      📅 {selectedTask.activeDate || selectedTask.scheduledDate || selectedTask.date} · {formatTimeRange(selectedTask.startHour, selectedTask.durationHours)}
                    </span>
                  </div>
                  {selectedTask.recurrence && selectedTask.recurrence !== 'none' && (
                    <div className="detail-info-item" style={{ gridColumn: 'span 2' }}>
                      <span className="detail-label">Chu kỳ lặp lại</span>
                      <span className="detail-value detail-value--repeat">
                        🔁 {getRecurrenceLabel(selectedTask)}
                      </span>
                    </div>
                  )}
                  {selectedTask.location && (
                    <div className="detail-info-item">
                      <span className="detail-label">Địa điểm / Nền tảng</span>
                      <span className="detail-value">{selectedTask.location}</span>
                    </div>
                  )}
                </div>

                {/* Structured Academic Detail Analysis Cards */}
                <div className="detail-academic-section">
                  <div className="detail-academic-header">
                    <span className="detail-academic-icon">🎓</span>
                    <span className="detail-academic-title">PHÂN TÍCH CHUYÊN SÂU & MỤC TIÊU HỌC TẬP</span>
                  </div>

                  <div className="detail-metric-grid">
                    <div className="detail-metric-card detail-metric-card--grade">
                      <div className="metric-card-header">
                        <span className="metric-card-icon">🎯</span>
                        <span className="metric-card-label">Mục tiêu điểm số</span>
                      </div>
                      <div className="metric-card-value">
                        {selectedTask.targetGrade || 'Điểm 8.5+ (Điểm A) - Nắm vững kiến thức trọng tâm'}
                      </div>
                    </div>

                    <div className="detail-metric-card detail-metric-card--effort">
                      <div className="metric-card-header">
                        <span className="metric-card-icon">⚡</span>
                        <span className="metric-card-label">Mức độ nỗ lực</span>
                      </div>
                      <div className="metric-card-value">
                        {selectedTask.effort || '4 - 6 giờ/tuần ôn tập chủ động'}
                      </div>
                    </div>

                    <div className="detail-metric-card detail-metric-card--weight">
                      <div className="metric-card-header">
                        <span className="metric-card-icon">⚖️</span>
                        <span className="metric-card-label">Độ nặng môn học</span>
                      </div>
                      <div className="metric-card-value">
                        {selectedTask.courseWeight || '3 - 4 tín chỉ trọng tâm'}
                      </div>
                    </div>

                    <div className="detail-metric-card detail-metric-card--practical">
                      <div className="metric-card-header">
                        <span className="metric-card-icon">💼</span>
                        <span className="metric-card-label">Giá trị thực tế</span>
                      </div>
                      <div className="metric-card-value">
                        {selectedTask.practicalValue || 'Nền tảng quan trọng cho đồ án và phỏng vấn kỹ thuật'}
                      </div>
                    </div>
                  </div>

                  {/* Specific Learning Checklist / Roadmap */}
                  {(selectedTask.specificLearning || selectedTask.topics) && (
                    <div className="detail-roadmap-box">
                      <div className="roadmap-box-header">
                        <span className="roadmap-box-icon">📖</span>
                        <span className="roadmap-box-title">Học cái gì cụ thể (Roadmap buổi học)</span>
                      </div>
                      <div className="roadmap-box-content">
                        {(selectedTask.specificLearning || selectedTask.topics).split(/\n|;/).map((line, idx) => {
                          const trimmed = line.trim()
                          if (!trimmed) return null
                          return (
                            <div key={idx} className="roadmap-step-item">
                              <span className="roadmap-step-bullet">•</span>
                              <span className="roadmap-step-text">{trimmed}</span>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  )}

                  {/* References Box */}
                  {selectedTask.references && (
                    <div className="detail-references-box">
                      <div className="references-box-header">
                        <span className="references-box-icon">📚</span>
                        <span className="references-box-title">Tài liệu tham khảo</span>
                      </div>
                      <div className="references-box-content">
                        {selectedTask.references}
                      </div>
                    </div>
                  )}

                  {/* Additional Description if distinct */}
                  {selectedTask.description && !selectedTask.description.startsWith('🎯') && (
                    <div className="detail-desc-box">
                      <span className="detail-label">Ghi chú bổ sung</span>
                      <p className="detail-desc-text">{selectedTask.description}</p>
                    </div>
                  )}
                </div>

                <div className="detail-actions">
                  <button
                    type="button"
                    className={`button button--sm ${(selectedTask.isCompletedOnDate ?? selectedTask.completed) ? 'button--secondary' : 'button--primary'}`}
                    onClick={() => toggleTaskComplete(selectedTask.id, selectedTask.activeDate)}
                  >
                    {(selectedTask.isCompletedOnDate ?? selectedTask.completed)
                      ? 'Mở lại công việc'
                      : selectedTask.recurrence && selectedTask.recurrence !== 'none'
                      ? '✓ Đánh dấu đã xong hôm nay'
                      : '✓ Đánh dấu đã học xong'}
                  </button>

                  <button
                    type="button"
                    className="button button--secondary button--sm button--danger"
                    onClick={() => deleteTask(selectedTask.id)}
                  >
                    {selectedTask.isDraft ? 'Xóa khỏi bản nháp' : 'Xóa task'}
                  </button>

                  <button
                    type="button"
                    className="button button--secondary button--sm"
                    onClick={() => { setSelectedTask(null); setIsEditingTask(false); }}
                  >
                    Đóng
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Quick Add Task Modal */}
      {showAddModal && (
        <div className="modal-overlay" onClick={() => setShowAddModal(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            {/* Modal Header */}
            <div className="modal-header">
              <div>
                <span className="modal-eyebrow">
                  <span className="modal-eyebrow-icon">📅</span> LÊN LỊCH THEO GIỜ
                </span>
                <h3 className="modal-title">Thêm công việc vào thời gian biểu</h3>
                <p className="modal-subtitle">
                  Kế hoạch cho ngày <strong>{addSlotData.date}</strong>
                  {isViewingMockup && <span className="modal-mockup-tag"> (Chế độ mô phỏng)</span>}
                </p>
              </div>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowAddModal(false)}
                aria-label="Đóng popup"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateTask} className="modal-form">
              {/* Task Type Switcher Cards */}
              <div className="form-group">
                <label className="form-label">Phân loại công việc</label>
                <div className="type-cards-grid type-cards-grid--4">
                  <button
                    type="button"
                    className={`type-card ${newForm.type === 'focus' ? 'type-card--focus-active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, type: 'focus' }))}
                  >
                    <div className="type-card-indicator type-card-indicator--focus" />
                    <div className="type-card-text">
                      <span className="type-card-title">Focus Time (AI)</span>
                      <span className="type-card-desc">Khối xanh lá · Ôn tập trọng tâm</span>
                    </div>
                  </button>

                  <button
                    type="button"
                    className={`type-card ${newForm.type === 'other' ? 'type-card--other-active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, type: 'other' }))}
                  >
                    <div className="type-card-indicator type-card-indicator--other" />
                    <div className="type-card-text">
                      <span className="type-card-title">Lịch trình khác</span>
                      <span className="type-card-desc">Khối màu xám · Tiết học, tự học</span>
                    </div>
                  </button>

                  <button
                    type="button"
                    className={`type-card ${newForm.type === 'routine' || newForm.type === 'lunch' ? 'type-card--routine-active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, type: 'routine' }))}
                  >
                    <div className="type-card-indicator type-card-indicator--routine" />
                    <div className="type-card-text">
                      <span className="type-card-title">Sinh hoạt cá nhân</span>
                      <span className="type-card-desc">Khối màu vàng · Ăn uống, vệ sinh...</span>
                    </div>
                  </button>

                  <button
                    type="button"
                    className={`type-card ${newForm.type === 'meeting' ? 'type-card--meeting-active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, type: 'meeting' }))}
                  >
                    <div className="type-card-indicator type-card-indicator--meeting" />
                    <div className="type-card-text">
                      <span className="type-card-title">Họp nhóm (Meeting)</span>
                      <span className="type-card-desc">Khối màu tím/xanh · Có checkmark</span>
                    </div>
                  </button>
                </div>
              </div>

              {/* Task Title */}
              <div className="form-group">
                <label className="form-label" htmlFor="task-title-input">
                  Tên công việc <span className="required-star">*</span>
                </label>
                <input
                  id="task-title-input"
                  className="form-input"
                  placeholder="Ví dụ: Tiết học trên lớp, Ôn tập Giải tích 2, hoặc Ăn trưa"
                  required
                  value={newForm.title}
                  onChange={e => setNewForm(f => ({ ...f, title: e.target.value }))}
                />
              </div>

              {/* Subject with Goals Dropdown / Datalist */}
              <div className="form-group">
                <div className="form-label-row">
                  <label className="form-label" htmlFor="task-subject-input">
                    Môn học / Phân loại
                  </label>
                  {goals.length > 0 && (
                    <span className="form-label-hint">
                      (Đã đồng bộ {goals.length} môn từ mục tiêu US-01)
                    </span>
                  )}
                </div>
                <div className="subject-input-wrapper">
                  <input
                    id="task-subject-input"
                    className="form-input"
                    list="goals-datalist"
                    placeholder="Ví dụ: Giải tích 2, Cơ sở dữ liệu, Sinh hoạt cá nhân..."
                    value={newForm.subject}
                    onChange={e => setNewForm(f => ({ ...f, subject: e.target.value }))}
                  />
                  {goals.length > 0 && (
                    <datalist id="goals-datalist">
                      {goals.map(g => (
                        <option key={g.id} value={g.subject} />
                      ))}
                    </datalist>
                  )}
                </div>
              </div>

              {/* Lặp lại định kỳ (Repetitive / Recurrence) */}
              <div className="form-group recurrence-group">
                <div className="form-label-row">
                  <label className="form-label">
                    <span className="recurrence-label-icon">🔁</span> Lặp lại định kỳ (Môn học cố định, sinh hoạt...)
                  </label>
                  <span className="form-label-hint">Lặp lại cùng giờ vào các ngày trong tuần</span>
                </div>

                <div className="recurrence-options-grid">
                  <button
                    type="button"
                    className={`recurrence-chip ${newForm.recurrence === 'none' ? 'recurrence-chip--active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, recurrence: 'none' }))}
                  >
                    Không lặp lại
                  </button>
                  <button
                    type="button"
                    className={`recurrence-chip ${newForm.recurrence === 'weekly' ? 'recurrence-chip--active' : ''}`}
                    onClick={() => {
                      const dow = new Date(`${addSlotData.date}T12:00:00`).getDay()
                      setNewForm(f => ({ ...f, recurrence: 'weekly', recurringDays: [dow] }))
                    }}
                  >
                    Hàng tuần (Thứ {new Date(`${addSlotData.date}T12:00:00`).getDay() === 0 ? 'CN' : new Date(`${addSlotData.date}T12:00:00`).getDay() + 1})
                  </button>
                  <button
                    type="button"
                    className={`recurrence-chip ${newForm.recurrence === 'weekdays' ? 'recurrence-chip--active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, recurrence: 'weekdays', recurringDays: [1, 2, 3, 4, 5] }))}
                  >
                    T2 – T6 (Trong tuần)
                  </button>
                  <button
                    type="button"
                    className={`recurrence-chip ${newForm.recurrence === 'daily' ? 'recurrence-chip--active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, recurrence: 'daily', recurringDays: [0, 1, 2, 3, 4, 5, 6] }))}
                  >
                    Mỗi ngày (Cả tuần)
                  </button>
                  <button
                    type="button"
                    className={`recurrence-chip ${newForm.recurrence === 'custom' ? 'recurrence-chip--active' : ''}`}
                    onClick={() => setNewForm(f => ({ ...f, recurrence: 'custom' }))}
                  >
                    Tùy chọn ngày…
                  </button>
                </div>

                {/* Day-of-week pills for custom selection */}
                {newForm.recurrence === 'custom' && (
                  <div className="recurrence-days-pills">
                    {[
                      { dow: 1, label: 'T2' },
                      { dow: 2, label: 'T3' },
                      { dow: 3, label: 'T4' },
                      { dow: 4, label: 'T5' },
                      { dow: 5, label: 'T6' },
                      { dow: 6, label: 'T7' },
                      { dow: 0, label: 'CN' },
                    ].map(({ dow, label }) => {
                      const isSelected = newForm.recurringDays.includes(dow)
                      return (
                        <button
                          key={dow}
                          type="button"
                          className={`day-pill ${isSelected ? 'day-pill--active' : ''}`}
                          onClick={() => {
                            setNewForm(f => {
                              const days = isSelected
                                ? f.recurringDays.filter(d => d !== dow)
                                : [...f.recurringDays, dow]
                              return { ...f, recurringDays: days.length > 0 ? days : [dow] }
                            })
                          }}
                        >
                          {label}
                        </button>
                      )
                    })}
                  </div>
                )}
              </div>

              {/* Date, Start Hour & Duration Grid */}
              <div className="form-row-grid">
                <div className="form-group">
                  <label className="form-label" htmlFor="task-date-input">
                    Ngày bắt đầu
                  </label>
                  <input
                    id="task-date-input"
                    type="date"
                    className="form-input"
                    value={addSlotData.date}
                    onChange={e => {
                      const newDate = e.target.value
                      setAddSlotData(prev => ({ ...prev, date: newDate }))
                      if (newForm.recurrence === 'weekly') {
                        const dow = new Date(`${newDate}T12:00:00`).getDay()
                        setNewForm(f => ({ ...f, recurringDays: [dow] }))
                      }
                    }}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="task-hour-select">
                    Giờ bắt đầu
                  </label>
                  <select
                    id="task-hour-select"
                    className="form-select"
                    value={newForm.startHour}
                    onChange={e => setNewForm(f => ({ ...f, startHour: Number(e.target.value) }))}
                  >
                    {HOURS.map(h => (
                      <option key={h} value={h}>{formatHour(h)} ({h}:00)</option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="task-duration-select">
                    Thời lượng
                  </label>
                  <select
                    id="task-duration-select"
                    className="form-select"
                    value={newForm.durationHours}
                    onChange={e => setNewForm(f => ({ ...f, durationHours: Number(e.target.value) }))}
                  >
                    <option value={0.5}>30 phút (0.5h)</option>
                    <option value={1}>1 giờ (60 phút)</option>
                    <option value={1.5}>1.5 giờ (90 phút)</option>
                    <option value={2}>2 giờ (120 phút)</option>
                    <option value={2.5}>2.5 giờ (150 phút)</option>
                    <option value={3}>3 giờ (180 phút)</option>
                  </select>
                </div>
              </div>

              {/* Time Range Preview Banner */}
              <div className="time-preview-banner">
                <span className="time-preview-icon">⏱</span>
                <span>
                  Khung giờ dự kiến: <strong>{formatTimeRange(newForm.startHour, newForm.durationHours)}</strong>
                  {newForm.recurrence && newForm.recurrence !== 'none' ? (
                    <span> · 🔁 <strong>{getRecurrenceLabel(newForm)}</strong></span>
                  ) : (
                    <span> ngày <strong>{addSlotData.date}</strong></span>
                  )}
                </span>
              </div>

              {/* Topics / Notes */}
              <div className="form-group">
                <label className="form-label" htmlFor="task-topics-input">
                  Chủ đề / Ghi chú ôn tập (Tùy chọn)
                </label>
                <textarea
                  id="task-topics-input"
                  className="form-textarea"
                  rows={2}
                  placeholder="Ví dụ: Giảng đường H2-301, làm bài tập mẫu hoặc ăn uống giải lao..."
                  value={newForm.topics}
                  onChange={e => setNewForm(f => ({ ...f, topics: e.target.value }))}
                />
              </div>

              {/* Modal Footer Actions */}
              <div className="modal-actions-bar">
                <button
                  type="button"
                  className="button button--secondary"
                  onClick={() => setShowAddModal(false)}
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  className="button button--primary"
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="12" y1="5" x2="12" y2="19"/>
                    <line x1="5" y1="12" x2="19" y2="12"/>
                  </svg>
                  <span>{isViewingMockup ? 'Thêm vào lịch mô phỏng' : 'Lưu vào lịch học'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
