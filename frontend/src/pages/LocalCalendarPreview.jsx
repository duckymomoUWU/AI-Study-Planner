import { useEffect, useState } from 'react'
import { listGoals } from '../lib/goals.js'
import './LocalCalendarPreview.css'

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

const STORAGE_KEY_MOCKUP = 'ai_study_planner_calendar_tasks_mockup_v4'

// Exact reference mockup calendar tasks matching May 17-19, 2026 screenshot
function getMockupCalendarTasks() {
  return [
    // ==========================================
    // MON 17 MAY 2026
    // ==========================================
    {
      id: 'mock-mon-1',
      title: 'Học trên lớp: Kiến trúc máy tính & Vi xử lý',
      overviewLabel: '', // Clean gray block in overview
      subject: 'Khoa học máy tính',
      type: 'other', // Gray block
      date: '2026-05-17',
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
      date: '2026-05-17',
      startHour: 11.0,
      durationHours: 1.0, // 11:00 - 12:00
      completed: true,
      topics: 'Ăn trưa, vệ sinh cá nhân và nghỉ ngơi nạp lại năng lượng',
      recurrence: 'daily',
      recurringDays: [0, 1, 2, 3, 4, 5, 6], // Lặp lại mỗi ngày
    },
    {
      id: 'mock-mon-3',
      title: 'Tự học ngoại ngữ & Đọc tin tức công nghệ',
      overviewLabel: '', // Clean gray block
      subject: 'Ngoại ngữ & Kỹ năng',
      type: 'other',
      date: '2026-05-17',
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
      date: '2026-05-17',
      startHour: 13.0,
      durationHours: 2.6, // 13:00 - 15:36
      completed: false,
      topics: 'Định lý Green, công thức Stokes, giải 5 bài tập mẫu đề thi cuối kỳ',
      source: 'ai',
    },
    {
      id: 'mock-mon-5',
      title: 'Chạy bộ & Rèn luyện thể lực',
      overviewLabel: '', // Clean gray block
      subject: 'Thể thao & Sức khỏe',
      type: 'other',
      date: '2026-05-17',
      startHour: 16.05,
      durationHours: 0.85, // 16:03 - 16:54
      completed: false,
      topics: 'Chạy cự ly 3km quanh sân vận động trường',
    },

    // ==========================================
    // TUE 18 MAY 2026
    // ==========================================
    {
      id: 'mock-tue-1',
      title: 'Làm việc nhóm & Chuẩn bị tài liệu môn học',
      overviewLabel: '', // Clean gray block
      subject: 'Kỹ năng làm việc nhóm',
      type: 'other',
      date: '2026-05-18',
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
      date: '2026-05-18',
      startHour: 10.0,
      durationHours: 3.25, // 10:00 - 13:15
      completed: false,
      topics: 'Thực hành chuẩn hóa 1NF - BCNF, giải trọn vẹn đề thi giữa kỳ năm 2025',
      source: 'ai',
    },
    {
      id: 'mock-tue-3',
      title: 'Thư giãn & Cafe đầu giờ chiều',
      overviewLabel: '', // Clean gray block
      subject: 'Sinh hoạt cá nhân',
      type: 'other',
      date: '2026-05-18',
      startHour: 13.25,
      durationHours: 0.65, // 13:15 - 13:54
      completed: false,
      topics: 'Giải lao nạp năng lượng',
    },
    {
      id: 'mock-tue-4',
      title: 'Ăn uống & Sinh hoạt cá nhân',
      overviewLabel: 'Sinh hoạt cá nhân',
      subject: 'Sinh hoạt cá nhân',
      type: 'routine',
      date: '2026-05-18',
      startHour: 14.0,
      durationHours: 1.0, // 14:00 - 15:00
      completed: true,
      topics: 'Bữa trưa, vệ sinh cá nhân và thư giãn giải lao',
      recurrence: 'daily',
      recurringDays: [0, 1, 2, 3, 4, 5, 6],
    },
    {
      id: 'mock-tue-5',
      title: 'Trao đổi nhanh đồ án với bạn cùng nhóm',
      overviewLabel: '', // Clean gray block
      subject: 'Đồ án CNPM',
      type: 'other',
      date: '2026-05-18',
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
      date: '2026-05-18',
      startHour: 16.05,
      durationHours: 0.85, // 16:03 - 16:54
      completed: false,
      topics: 'Nghe tư vấn lộ trình học kỳ chuyên ngành',
    },

    // ==========================================
    // WED 19 MAY 2026
    // ==========================================
    {
      id: 'mock-wed-1',
      title: 'Kiểm tra email & Lên danh sách việc cần làm',
      overviewLabel: '', // Clean gray block
      subject: 'Kế hoạch cá nhân',
      type: 'other',
      date: '2026-05-19',
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
      date: '2026-05-19',
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
      date: '2026-05-19',
      startHour: 12.35,
      durationHours: 0.55, // 12:21 - 12:54
      completed: true,
      topics: 'Ăn trưa nhẹ, rửa mặt, vệ sinh cá nhân chuẩn bị cho buổi học chiều',
      recurrence: 'daily',
      recurringDays: [0, 1, 2, 3, 4, 5, 6],
    },
    {
      id: 'mock-wed-4',
      title: 'Focus Time: Ôn tập Transaction & Concurrency Control',
      overviewLabel: 'Focus Time',
      subject: 'Cơ sở dữ liệu',
      type: 'focus', // Vibrant green block (Web app generated)
      date: '2026-05-19',
      startHour: 13.05,
      durationHours: 2.0, // 13:03 - 15:03
      completed: false,
      topics: 'Khóa 2 pha 2PL, kiểm tra mức cô lập Read Committed / Serializable',
      source: 'ai',
    },
    {
      id: 'mock-wed-5',
      title: 'Đọc tài liệu React Hooks & Vite Build',
      overviewLabel: '', // Clean gray block
      subject: 'Công nghệ Web',
      type: 'other',
      date: '2026-05-19',
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
      date: '2026-05-19',
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
  if (!key || typeof window !== 'undefined') {
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
  }
  return []
}

// Recurrence Helpers:
function isTaskOnDate(task, dateObj) {
  const dateStr = toDateKey(dateObj)
  const dow = dateObj.getDay()

  // 1. If not recurring, match exact date
  if (!task.recurrence || task.recurrence === 'none') {
    return task.date === dateStr
  }

  // 2. Date must be on or after task start date
  const taskStartDate = task.startDate || task.date
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
    const originDow = new Date(`${taskStartDate}T12:00:00`).getDay()
    return dow === originDow
  }
  if (task.recurrence === 'custom') {
    return Array.isArray(task.recurringDays) && task.recurringDays.includes(dow)
  }

  return task.date === dateStr
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

const HOURS = [9, 10, 11, 12, 13, 14, 15, 16, 17]

function formatHour(h) {
  if (h === 12) return '12pm'
  if (h > 12) return `${h - 12}pm`
  return `${h}am`
}

function formatTimeRange(startHour, durationHours) {
  const startH = Math.floor(startHour)
  const startM = Math.round((startHour - startH) * 60)
  const endTotal = startHour + durationHours
  const endH = Math.floor(endTotal)
  const endM = Math.round((endTotal - endH) * 60)

  const fmt = (h, m) => {
    const period = h >= 12 ? 'pm' : 'am'
    const displayH = h > 12 ? h - 12 : h === 0 ? 12 : h
    const displayM = m > 0 ? `:${String(m).padStart(2, '0')}` : ''
    return `${displayH}${displayM}${period}`
  }

  return `${fmt(startH, startM)} - ${fmt(endH, endM)} (${Math.round(durationHours * 60)} phút)`
}

export default function LocalCalendarPreview({ currentUser, onBack, onNavigateAuth }) {
  // If not logged in, always view mockup; if logged in, allow manual toggle
  const [userMockupOverride, setUserMockupOverride] = useState(false)
  const isViewingMockup = !currentUser || userMockupOverride

  // Separate date state for mockup vs real schedule
  const [mockupBaseDate, setMockupBaseDate] = useState(() => new Date(2026, 4, 17, 12, 0, 0))
  const [userBaseDate, setUserBaseDate] = useState(() => new Date())

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

  // Selected task for detail popup
  const [selectedTask, setSelectedTask] = useState(null)
  const [goals, setGoals] = useState([])
  const [showAddModal, setShowAddModal] = useState(false)
  const [addSlotData, setAddSlotData] = useState(() => ({
    date: !currentUser ? '2026-05-17' : toDateKey(new Date()),
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
  const activeTasks = isViewingMockup ? mockupTasks : userTasks

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
    setMockupBaseDate(new Date(2026, 4, 17, 12, 0, 0))
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

    if (isViewingMockup) {
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

    if (isViewingMockup) {
      setMockupTasks(updater)
    } else {
      setUserTasks(updater)
    }
  }

  function deleteTask(taskId) {
    if (!window.confirm('Bạn có chắc muốn xóa công việc này khỏi lịch học?')) return
    const filterer = prev => prev.filter(t => t.id !== taskId)
    if (isViewingMockup) {
      setMockupTasks(filterer)
    } else {
      setUserTasks(filterer)
    }
    setSelectedTask(null)
  }

  return (
    <div className="cal-container" aria-labelledby="cal-title">
      {/* Mockup Banner (Shown ONLY when not logged in or explicitly viewing mockup) */}
      {!currentUser && (
        <div className="cal-mockup-banner">
          <div className="cal-mockup-banner-content">
            <span className="cal-mockup-pill">MOCKUP DEMO</span>
            <span className="cal-mockup-text">
              <strong>Lịch mẫu tháng 5/2026:</strong> Dữ liệu thử nghiệm lưu trên trình duyệt, không đồng bộ với backend. Đang hiển thị bản mô phỏng giao diện chuẩn theo thiết kế. Bấm vào từng ô để xem chi tiết hoặc thêm công việc thử nghiệm.
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
                date: isViewingMockup ? '2026-05-17' : toDateKey(new Date()),
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
              onClick={() => setBaseDate(new Date(2026, 4, 17, 12, 0, 0))}
              title="Về ngày mẫu 17-19 tháng 5/2026"
            >
              Về mốc 17/05/2026
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
                      className="cal-hour-slot"
                      onClick={() => handleSlotClick(dateStr, hour)}
                      title={`Bấm để thêm task vào lúc ${formatHour(hour)}`}
                    />
                  ))}

                  {/* Render Task Blocks inside day column */}
                  {dayTasks.map(task => {
                    const topOffset = (task.startHour - 9) * 64 // 64px per hour starting at 9am
                    const blockHeight = Math.max(28, task.durationHours * 64 - 4)
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
                    const displayLabel = (task.overviewLabel !== undefined && task.overviewLabel !== '' && isViewingMockup)
                      ? task.overviewLabel
                      : (task.title || task.overviewLabel || 'Buổi học')

                    return (
                      <div
                        key={`${task.id}-${dateStr}`}
                        className={`cal-task-block ${blockClass} ${isSelected ? 'cal-task-block--selected' : ''}`}
                        style={{
                          top: `${topOffset}px`,
                          height: `${blockHeight}px`,
                        }}
                        onClick={(e) => {
                          e.stopPropagation()
                          setSelectedTask({
                            ...task,
                            activeDate: dateStr,
                            isCompletedOnDate: isCompleted,
                          })
                        }}
                        role="button"
                        tabIndex={0}
                        title={`${task.title}${task.recurrence && task.recurrence !== 'none' ? ` (${getRecurrenceLabel(task)})` : ''}`}
                      >
                        <div className="cal-task-content">
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
        <div className="detail-modal-overlay" onClick={() => setSelectedTask(null)}>
          <div className="detail-modal-card" onClick={e => e.stopPropagation()}>
            <div className="detail-modal-header">
              <div className="detail-badge-group">
                <span className={`detail-type-badge badge--${selectedTask.type}`}>
                  {selectedTask.type === 'focus' || selectedTask.type === 'app'
                    ? '✦ Task AI Study Planner (Focus Time)'
                    : selectedTask.type === 'routine' || selectedTask.type === 'lunch'
                    ? '☕ Sinh hoạt cá nhân (Ăn uống, vệ sinh)'
                    : selectedTask.type === 'meeting'
                    ? '👥 Cuộc họp / Nhóm (Meeting)'
                    : '📌 Lịch trình khác'}
                </span>
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
                onClick={() => setSelectedTask(null)}
                aria-label="Đóng popup chi tiết"
              >
                ✕
              </button>
            </div>

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
                    📅 {selectedTask.activeDate || selectedTask.date} · {formatTimeRange(selectedTask.startHour, selectedTask.durationHours)}
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
                {selectedTask.topics && (
                  <div className="detail-info-item" style={{ gridColumn: 'span 2' }}>
                    <span className="detail-label">Nội dung ôn tập / Chi tiết</span>
                    <span className="detail-value">{selectedTask.topics}</span>
                  </div>
                )}
                {selectedTask.location && (
                  <div className="detail-info-item">
                    <span className="detail-label">Địa điểm / Nền tảng</span>
                    <span className="detail-value">{selectedTask.location}</span>
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
                  Xóa task
                </button>

                <button
                  type="button"
                  className="button button--secondary button--sm"
                  onClick={() => setSelectedTask(null)}
                >
                  Đóng
                </button>
              </div>
            </div>
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
