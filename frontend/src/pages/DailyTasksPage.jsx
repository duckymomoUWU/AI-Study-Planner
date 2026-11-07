import { useEffect, useState } from 'react'
import { listGoals } from '../lib/goals.js'
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

const STORAGE_KEY = 'ai_study_planner_calendar_tasks_v2'

// Initial seed calendar tasks matching the user's reference mockup
function getInitialCalendarTasks() {
  const today = new Date()
  const d0 = toDateKey(today)
  const d1 = toDateKey(addDays(today, 1))
  const d2 = toDateKey(addDays(today, 2))

  return [
    // Ngày 1 (Hôm nay)
    {
      id: 'cal-task-1',
      title: 'Học trên lớp: Kiến trúc máy tính',
      subject: 'Khoa học máy tính',
      type: 'other', // Gray block
      date: d0,
      startHour: 9,
      durationHours: 2, // 9am - 11am
      location: 'Phòng B204',
      completed: false,
    },
    {
      id: 'cal-task-2',
      title: 'Nghỉ trưa & Ăn trưa',
      subject: 'Sinh hoạt cá nhân',
      type: 'other', // Gray block
      date: d0,
      startHour: 11.5,
      durationHours: 0.8, // 11:30am - 12:20pm
      completed: true,
    },
    {
      id: 'cal-task-3',
      title: 'Tự học tiếng Anh',
      subject: 'Ngoại ngữ',
      type: 'other',
      date: d0,
      startHour: 12.5,
      durationHours: 1,
      completed: false,
    },
    {
      id: 'cal-task-4',
      title: 'Ôn tập Tích phân đường loại 1 & 2',
      subject: 'Giải tích 2',
      type: 'app', // Blue block (from AI Study Planner)
      date: d0,
      startHour: 13.5,
      durationHours: 2.5, // 1:30pm - 4:00pm
      completed: false,
      topics: 'Định lý Green, công thức Stokes, giải 5 bài tập mẫu',
      source: 'ai',
    },
    {
      id: 'cal-task-5',
      title: 'Thể dục & Thư giãn',
      subject: 'Sức khỏe',
      type: 'other',
      date: d0,
      startHour: 16.5,
      durationHours: 1,
      completed: false,
    },

    // Ngày 2 (Hôm sau)
    {
      id: 'cal-task-6',
      title: 'Làm việc nhóm & Đọc tài liệu',
      subject: 'Kỹ năng mềm',
      type: 'other', // Gray block
      date: d1,
      startHour: 9,
      durationHours: 1, // 9am - 10am
      completed: true,
    },
    {
      id: 'cal-task-7',
      title: 'Luyện đề SQL & Chuẩn hóa quan hệ BCNF',
      subject: 'Cơ sở dữ liệu',
      type: 'app', // Blue block (from AI Study Planner)
      date: d1,
      startHour: 10,
      durationHours: 3, // 10am - 1pm
      completed: false,
      topics: 'Thực hành chuẩn hóa 1NF - BCNF, giải đề thi giữa kỳ',
      source: 'ai',
    },
    {
      id: 'cal-task-8',
      title: 'Nghỉ trưa & Cafe',
      subject: 'Sinh hoạt cá nhân',
      type: 'other',
      date: d1,
      startHour: 13.2,
      durationHours: 0.8,
      completed: false,
    },
    {
      id: 'cal-task-9',
      title: 'Họp cố vấn học tập',
      subject: 'Công việc trường',
      type: 'other',
      date: d1,
      startHour: 14.5,
      durationHours: 1.2,
      completed: false,
    },
    {
      id: 'cal-task-10',
      title: 'Giải đề thi thử Phương trình vi phân',
      subject: 'Giải tích 2',
      type: 'app', // Blue block (from AI Study Planner)
      date: d1,
      startHour: 16,
      durationHours: 1.5,
      completed: false,
      topics: 'Phương trình vi phân tuyến tính cấp 2 hệ số hằng',
      source: 'manual',
    },

    // Ngày 3 (Hôm sau nữa)
    {
      id: 'cal-task-11',
      title: 'Kiểm tra bài tập lớn',
      subject: 'Mạng máy tính',
      type: 'other',
      date: d2,
      startHour: 9,
      durationHours: 0.8,
      completed: true,
    },
    {
      id: 'cal-task-12',
      title: 'Thực hành Lab Hệ điều hành',
      subject: 'Hệ điều hành',
      type: 'other',
      date: d2,
      startHour: 10,
      durationHours: 2.5,
      completed: false,
    },
    {
      id: 'cal-task-13',
      title: 'Nghỉ trưa',
      subject: 'Sinh hoạt',
      type: 'other',
      date: d2,
      startHour: 12.8,
      durationHours: 0.7,
      completed: true,
    },
    {
      id: 'cal-task-14',
      title: 'Ôn tập Transaction & Concurrency Control',
      subject: 'Cơ sở dữ liệu',
      type: 'app', // Blue block (from AI Study Planner)
      date: d2,
      startHour: 13.5,
      durationHours: 2,
      completed: false,
      topics: 'Khóa 2 pha 2PL, mức cô lập Read Committed / Serializable',
      source: 'ai',
    },
    {
      id: 'cal-task-15',
      title: 'Họp nhóm Sprint Review Dự án',
      subject: 'Đồ án CNPM',
      type: 'app', // Blue block
      date: d2,
      startHour: 16,
      durationHours: 1.2,
      completed: false,
      topics: 'Báo cáo tiến độ Sprint 2 và demo sản phẩm',
      source: 'manual',
    },
  ]
}

const HOURS = [9, 10, 11, 12, 13, 14, 15, 16, 17, 18]

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

export default function DailyTasksPage({ onBack }) {
  const [baseDate, setBaseDate] = useState(() => new Date())
  const [todayKey] = useState(() => toDateKey(new Date()))
  const [viewDaysCount, setViewDaysCount] = useState(3) // 3 days (matches mockup) or 7 days
  const [tasks, setTasks] = useState(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (saved) return JSON.parse(saved)
    } catch {
      // fallback
    }
    return getInitialCalendarTasks()
  })

  // Selected task for detail popup
  const [selectedTask, setSelectedTask] = useState(null)
  const [goals, setGoals] = useState([])
  const [showAddModal, setShowAddModal] = useState(false)
  const [addSlotData, setAddSlotData] = useState(() => ({ date: toDateKey(new Date()), startHour: 10 }))

  // New task form state
  const [newForm, setNewForm] = useState({
    title: '',
    subject: 'Giải tích 2',
    type: 'app', // 'app' (blue) or 'other' (gray)
    startHour: 10,
    durationHours: 1.5,
    topics: '',
  })

  // Save tasks to localStorage on change
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks))
    } catch {
      // ignore
    }
  }, [tasks])

  // Load goals from API/storage for selector
  useEffect(() => {
    listGoals().then(res => setGoals(res)).catch(() => {})
  }, [])

  // Calculate visible columns (dates)
  const visibleDates = []
  for (let i = 0; i < viewDaysCount; i++) {
    visibleDates.push(addDays(baseDate, i))
  }

  // Header month-year title
  const monthYearTitle = new Intl.DateTimeFormat('vi-VN', {
    month: 'long',
    year: 'numeric',
  }).format(baseDate)

  function navigateDays(offset) {
    setBaseDate(prev => addDays(prev, offset))
  }

  function handleSlotClick(dateStr, hour) {
    setAddSlotData({ date: dateStr, startHour: hour })
    setNewForm(f => ({
      ...f,
      startHour: hour,
      subject: goals[0]?.subject || 'Giải tích 2',
    }))
    setShowAddModal(true)
  }

  function handleCreateTask(e) {
    e.preventDefault()
    if (!newForm.title.trim()) return

    const newTask = {
      id: `task-${Date.now()}`,
      title: newForm.title.trim(),
      subject: newForm.subject,
      type: newForm.type,
      date: addSlotData.date,
      startHour: Number(newForm.startHour),
      durationHours: Number(newForm.durationHours),
      topics: newForm.topics,
      completed: false,
      source: newForm.type === 'app' ? 'ai' : 'manual',
    }

    setTasks(prev => [...prev, newTask])
    setShowAddModal(false)
    setNewForm({
      title: '',
      subject: goals[0]?.subject || 'Giải tích 2',
      type: 'app',
      startHour: 10,
      durationHours: 1.5,
      topics: '',
    })
  }

  function toggleTaskComplete(taskId) {
    setTasks(prev => prev.map(t => {
      if (t.id === taskId) {
        const updated = { ...t, completed: !t.completed }
        if (selectedTask?.id === taskId) {
          setSelectedTask(updated)
        }
        return updated
      }
      return t
    }))
  }

  function deleteTask(taskId) {
    if (!window.confirm('Bạn có chắc muốn xóa công việc này khỏi lịch học?')) return
    setTasks(prev => prev.filter(t => t.id !== taskId))
    setSelectedTask(null)
  }

  return (
    <div className="cal-container" aria-labelledby="cal-title">
      {/* Top Controls: Back, View Switcher & Actions */}
      <div className="cal-top-bar">
        <div className="cal-top-left">
          <button type="button" className="button button--secondary button--sm" onClick={onBack}>
            ← Tổng quan
          </button>
          <div className="cal-legend">
            <span className="legend-item">
              <span className="legend-dot legend-dot--app" />
              <span>AI Study Planner (Xanh dương)</span>
            </span>
            <span className="legend-item">
              <span className="legend-dot legend-dot--other" />
              <span>Lịch trình khác (Xám)</span>
            </span>
          </div>
        </div>

        <div className="cal-top-right">
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
              setAddSlotData({ date: toDateKey(new Date()), startHour: 10 })
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
            className="button button--secondary button--sm"
            onClick={() => setBaseDate(new Date())}
          >
            Hôm nay
          </button>
          <h2 id="cal-title" className="cal-month-title">
            {monthYearTitle} ▾
          </h2>
        </div>

        <div className="cal-hint-text">
          <span>💡 Bấm vào ô bất kỳ để xem chi tiết hoặc thêm task.</span>
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
              const dayTasks = tasks.filter(t => t.date === dateStr)

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
                    const blockHeight = Math.max(34, task.durationHours * 64 - 4)
                    const isAppTask = task.type === 'app'
                    const isSelected = selectedTask?.id === task.id

                    return (
                      <div
                        key={task.id}
                        className={`cal-task-block ${isAppTask ? 'cal-task-block--app' : 'cal-task-block--other'} ${isSelected ? 'cal-task-block--selected' : ''}`}
                        style={{
                          top: `${topOffset}px`,
                          height: `${blockHeight}px`,
                        }}
                        onClick={(e) => {
                          e.stopPropagation()
                          setSelectedTask(task)
                        }}
                        role="button"
                        tabIndex={0}
                        title={`Bấm để xem chi tiết: ${task.title}`}
                      >
                        <div className="cal-task-content">
                          <span className="cal-task-title">{task.title}</span>
                          {task.completed && <span className="cal-task-check">✓</span>}
                        </div>
                        {blockHeight > 50 && (
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
                <span className={`detail-type-badge ${selectedTask.type === 'app' ? 'badge--app' : 'badge--other'}`}>
                  {selectedTask.type === 'app' ? '✦ Task AI Study Planner' : 'Lịch trình cá nhân khác'}
                </span>
                {selectedTask.completed && (
                  <span className="detail-status-badge">✓ Đã hoàn thành</span>
                )}
              </div>
              <button
                type="button"
                className="detail-close-btn"
                onClick={() => setSelectedTask(null)}
              >
                ✕
              </button>
            </div>

            <h3 className="detail-title">{selectedTask.title}</h3>

            <div className="detail-info-grid">
              <div className="detail-info-item">
                <span className="detail-label">Môn học / Phân loại</span>
                <span className="detail-value">{selectedTask.subject || 'Mục tiêu ôn tập'}</span>
              </div>
              <div className="detail-info-item">
                <span className="detail-label">Thời gian</span>
                <span className="detail-value">
                  📅 {selectedTask.date} · {formatTimeRange(selectedTask.startHour, selectedTask.durationHours)}
                </span>
              </div>
              {selectedTask.topics && (
                <div className="detail-info-item" style={{ gridColumn: 'span 2' }}>
                  <span className="detail-label">Nội dung ôn tập</span>
                  <span className="detail-value">{selectedTask.topics}</span>
                </div>
              )}
              {selectedTask.location && (
                <div className="detail-info-item">
                  <span className="detail-label">Địa điểm / Phòng học</span>
                  <span className="detail-value">{selectedTask.location}</span>
                </div>
              )}
            </div>

            <div className="detail-actions">
              <button
                type="button"
                className={`button button--sm ${selectedTask.completed ? 'button--secondary' : 'button--primary'}`}
                onClick={() => toggleTaskComplete(selectedTask.id)}
              >
                {selectedTask.completed ? 'Mở lại công việc' : '✓ Đánh dấu đã học xong'}
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
      )}

      {/* Quick Add Task Modal */}
      {showAddModal && (
        <div className="detail-modal-overlay" onClick={() => setShowAddModal(false)}>
          <div className="detail-modal-card" onClick={e => e.stopPropagation()}>
            <div className="detail-modal-header">
              <h3>Thêm lịch học vào thời gian biểu</h3>
              <button
                type="button"
                className="detail-close-btn"
                onClick={() => setShowAddModal(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateTask}>
              <div className="modal-field">
                <label>Loại công việc</label>
                <div className="type-selector-group">
                  <label className={`type-option ${newForm.type === 'app' ? 'type-option--active' : ''}`}>
                    <input
                      type="radio"
                      name="taskType"
                      value="app"
                      checked={newForm.type === 'app'}
                      onChange={() => setNewForm(f => ({ ...f, type: 'app' }))}
                    />
                    <span className="type-indicator type-indicator--app" />
                    <span>Task AI Study Planner (Xanh dương)</span>
                  </label>
                  <label className={`type-option ${newForm.type === 'other' ? 'type-option--active' : ''}`}>
                    <input
                      type="radio"
                      name="taskType"
                      value="other"
                      checked={newForm.type === 'other'}
                      onChange={() => setNewForm(f => ({ ...f, type: 'other' }))}
                    />
                    <span className="type-indicator type-indicator--other" />
                    <span>Lịch trình cá nhân khác (Xám)</span>
                  </label>
                </div>
              </div>

              <div className="modal-field">
                <label>Tên công việc / Môn học</label>
                <input
                  className="modal-input"
                  placeholder="Ví dụ: Ôn tập Tích phân đường hoặc Học trên trường"
                  required
                  value={newForm.title}
                  onChange={e => setNewForm(f => ({ ...f, title: e.target.value }))}
                />
              </div>

              <div className="modal-field">
                <label>Môn học liên quan</label>
                <input
                  className="modal-input"
                  placeholder="Ví dụ: Giải tích 2, Cơ sở dữ liệu..."
                  value={newForm.subject}
                  onChange={e => setNewForm(f => ({ ...f, subject: e.target.value }))}
                />
              </div>

              <div className="date-time-grid">
                <div className="modal-field">
                  <label>Giờ bắt đầu</label>
                  <select
                    className="modal-input"
                    value={newForm.startHour}
                    onChange={e => setNewForm(f => ({ ...f, startHour: Number(e.target.value) }))}
                  >
                    {HOURS.map(h => (
                      <option key={h} value={h}>{formatHour(h)}</option>
                    ))}
                  </select>
                </div>
                <div className="modal-field">
                  <label>Thời lượng (giờ)</label>
                  <select
                    className="modal-input"
                    value={newForm.durationHours}
                    onChange={e => setNewForm(f => ({ ...f, durationHours: Number(e.target.value) }))}
                  >
                    <option value={0.5}>30 phút (0.5h)</option>
                    <option value={1}>1 giờ</option>
                    <option value={1.5}>1.5 giờ (90 phút)</option>
                    <option value={2}>2 giờ</option>
                    <option value={2.5}>2.5 giờ</option>
                    <option value={3}>3 giờ</option>
                  </select>
                </div>
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="button button--secondary"
                  onClick={() => setShowAddModal(false)}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="button button--primary"
                >
                  Lưu vào lịch
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
