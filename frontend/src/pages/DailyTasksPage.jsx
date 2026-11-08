import { useCallback, useEffect, useState } from 'react'
import { listGoals } from '../lib/goals.js'
import { createTask, deleteTask, listOverdueTasks, listTasksByDate, setTaskCompleted, updateTask } from '../lib/tasks.js'
import { describeTaskError } from '../lib/taskErrors.js'
import { Badge } from '../components/ui/badge.jsx'
import { Checkbox } from '../components/ui/checkbox.jsx'
import './DailyTasksPage.css'

function getLocalDateString(d = new Date()) {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDayOfWeekAndDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(`${dateStr}T12:00:00`)
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(date)
}

function offsetDate(dateStr, days) {
  const date = new Date(`${dateStr}T12:00:00`)
  date.setDate(date.getDate() + days)
  return getLocalDateString(date)
}

export default function DailyTasksPage({ onBack, onNavigateGoals }) {
  const [selectedDate, setSelectedDate] = useState(getLocalDateString())
  const [tasksData, setTasksData] = useState({ items: [], totalEstimatedMinutes: 0, completedCount: 0, totalCount: 0, remainingCount: 0 })
  const [overdueTasks, setOverdueTasks] = useState([])
  const [goals, setGoals] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [showOverdue, setShowOverdue] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)
  const [editingTask, setEditingTask] = useState(null)
  const [taskDate, setTaskDate] = useState(selectedDate)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [taskFeedback, setTaskFeedback] = useState('')
  const [pendingIds, setPendingIds] = useState(() => new Set())

  // New task form state
  const [newTaskForm, setNewTaskForm] = useState({
    goalId: '',
    title: '',
    estimatedMinutes: 60,
  })

  const refreshData = useCallback(async (date) => {
    setLoading(true)
    setError('')
    try {
      const [dailyRes, overdueRes, goalsRes] = await Promise.all([
        listTasksByDate(date),
        listOverdueTasks(),
        listGoals().catch(() => []),
      ])
      setTasksData(dailyRes)
      setOverdueTasks(overdueRes)
      setGoals(goalsRes)
      if (goalsRes.length > 0 && !newTaskForm.goalId) {
        setNewTaskForm(f => ({ ...f, goalId: goalsRes[0].id }))
      }
    } catch (err) {
      if (err.status === 401) {
        setError('unauthenticated')
      } else {
        setError(err.message || 'Không thể tải dữ liệu lịch học.')
      }
    } finally {
      setLoading(false)
    }
  }, [newTaskForm.goalId])

  useEffect(() => {
    let ignore = false
    async function fetchInitial() {
      try {
        const [dailyRes, overdueRes, goalsRes] = await Promise.all([
          listTasksByDate(selectedDate),
          listOverdueTasks(),
          listGoals().catch(() => []),
        ])
        if (!ignore) {
          setTasksData(dailyRes)
          setOverdueTasks(overdueRes)
          setGoals(goalsRes)
          if (goalsRes.length > 0) {
            setNewTaskForm(f => ({ ...f, goalId: f.goalId || goalsRes[0].id }))
          }
        }
      } catch (err) {
        if (!ignore) {
          if (err.status === 401) {
            setError('unauthenticated')
          } else {
            setError(err.message || 'Không thể tải dữ liệu lịch học.')
          }
        }
      } finally {
        if (!ignore) {
          setLoading(false)
        }
      }
    }
    fetchInitial()
    return () => {
      ignore = true
    }
  }, [selectedDate])

  /**
   * Marks a task done or re-opens it. The row waits for the server (no optimistic change), is locked while the
   * request runs so it cannot be sent twice, and is replaced by the task the server stored.
   */
  async function handleToggle(task, nextCompleted) {
    if (pendingIds.has(task.id)) return
    setPendingIds(prev => new Set(prev).add(task.id))
    setError('')
    try {
      const stored = await setTaskCompleted(task.goalId, task.id, nextCompleted)
      setTasksData(prev => {
        const items = prev.items.map(t => (t.id === stored.id ? stored : t))
        const completedCount = items.filter(t => t.completed).length
        return { ...prev, items, completedCount, remainingCount: items.length - completedCount }
      })
      setTaskFeedback(nextCompleted ? 'Đã đánh dấu hoàn thành.' : 'Đã mở lại công việc.')
      setTimeout(() => setTaskFeedback(''), 2500)
    } catch (err) {
      const described = describeTaskError(err)
      if (described.reload) {
        // Reload first: refreshData clears the error, and the message must stay visible after the list syncs.
        await refreshData(selectedDate)
      }
      setError(described.unauthenticated ? 'unauthenticated' : described.message)
    } finally {
      setPendingIds(prev => {
        const next = new Set(prev)
        next.delete(task.id)
        return next
      })
    }
  }

  async function handleDelete(task) {
    if (!window.confirm(`Bạn có chắc muốn xóa công việc "${task.title}"?`)) return
    try {
      await deleteTask(task.goalId, task.id)
      setTaskFeedback('Đã xóa công việc.')
      setTimeout(() => setTaskFeedback(''), 3000)
      refreshData(selectedDate)
    } catch (err) {
      setError(describeTaskError(err).message || 'Không thể xóa công việc.')
    }
  }

  async function handleCreateTask(e) {
    e.preventDefault()
    if (!newTaskForm.title.trim()) return
    setIsSubmitting(true)
    try {
      const targetGoalId = newTaskForm.goalId || goals[0]?.id
      if (!targetGoalId) throw new Error('Bạn cần tạo mục tiêu trước.')
      const input = {
        title: newTaskForm.title.trim(),
        scheduledDate: taskDate,
        estimatedMinutes: Number(newTaskForm.estimatedMinutes),
      }
      if (editingTask) await updateTask(editingTask.id, input)
      else await createTask(targetGoalId, input)
      setShowAddModal(false)
      setNewTaskForm({ goalId: goals[0]?.id || '', title: '', estimatedMinutes: 60 })
      setEditingTask(null)
      setTaskFeedback('Đã lưu công việc vào lịch học.')
      setTimeout(() => setTaskFeedback(''), 3000)
      refreshData(selectedDate)
    } catch (err) {
      setError(describeTaskError(err).message || 'Không thể tạo công việc mới.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleMoveOverdueToToday(task) {
    try {
      await updateTask(task.id, { scheduledDate: getLocalDateString() })
      setTaskFeedback(`Đã chuyển công việc "${task.title}" sang hôm nay!`)
      setTimeout(() => setTaskFeedback(''), 3000)
      setSelectedDate(getLocalDateString())
      await refreshData(getLocalDateString())
    } catch (err) {
      setError(describeTaskError(err).message || 'Không thể dời công việc.')
    }
  }

  const subjectByGoal = Object.fromEntries(goals.map(g => [g.id, g.subject]))
  const todayStr = getLocalDateString()
  const isToday = selectedDate === todayStr
  const percentComplete = tasksData.totalCount > 0
    ? Math.round((tasksData.completedCount / tasksData.totalCount) * 100)
    : 0

  return (
    <div className="daily-page" aria-labelledby="daily-page-title">
      {/* Top Header & Navigation */}
      <div className="daily-header">
        <div>
          <div className="daily-eyebrow">SPRINT 2 · US-03</div>
          <h1 id="daily-page-title" className="daily-title">Lịch học theo ngày</h1>
          <p className="daily-subtitle">Theo dõi công việc cụ thể hôm nay, kiểm soát thời lượng ôn tập và ghi nhận tiến độ.</p>
        </div>
        <div className="daily-header-actions">
          <button type="button" className="button button--secondary" onClick={onBack}>
            ← Tổng quan
          </button>
          <button
            type="button"
            className="button button--primary"
            onClick={() => { setEditingTask(null); setTaskDate(selectedDate); setNewTaskForm({ goalId: goals[0]?.id || '', title: '', estimatedMinutes: 60 }); setShowAddModal(true) }}
          >
            + Thêm công việc
          </button>
        </div>
      </div>

      {/* Feedback Messages */}
      {taskFeedback && <div className="daily-alert daily-alert--success">{taskFeedback}</div>}
      {error === 'unauthenticated' && (
        <div className="daily-alert daily-alert--auth">
          <div><strong>Chưa đăng nhập:</strong> Vui lòng đăng nhập để lưu trữ và quản lý công việc của bạn.</div>
          <button type="button" className="button button--primary button--sm" onClick={() => { window.location.hash = '#login' }}>
            Đăng nhập ngay →
          </button>
        </div>
      )}
      {error && error !== 'unauthenticated' && (
        <div className="daily-alert daily-alert--error">{error}</div>
      )}

      {/* Overdue Task Banner (if any) */}
      {overdueTasks.length > 0 && (
        <div className="daily-overdue-banner">
          <div className="daily-overdue-content">
            <span className="overdue-icon">⚠️</span>
            <div>
              <strong>Bạn có {overdueTasks.length} công việc từ những ngày trước chưa hoàn thành!</strong>
              <p>Hãy xem lại và dời lịch để không bỏ lỡ kiến thức ôn tập.</p>
            </div>
          </div>
          <button
            type="button"
            className="button button--secondary button--sm"
            onClick={() => setShowOverdue(!showOverdue)}
          >
            {showOverdue ? 'Ẩn danh sách việc trễ' : `Xem ${overdueTasks.length} việc trễ`}
          </button>
        </div>
      )}

      {/* Expanded Overdue List */}
      {showOverdue && overdueTasks.length > 0 && (
        <div className="daily-overdue-list">
          <h3>Công việc chưa hoàn thành trong quá khứ</h3>
          {overdueTasks.map(ot => (
            <div key={ot.id} className="daily-task-item daily-task-item--overdue">
              <div className="task-info">
                <span className="task-subject-badge">{ot.goalSubject || 'Môn học'}</span>
                <span className="task-title">{ot.title}</span>
                <span className="task-date-tag">Lịch: {ot.scheduledDate} · {ot.estimatedMinutes} phút</span>
              </div>
              <button
                type="button"
                className="button button--primary button--sm"
                onClick={() => handleMoveOverdueToToday(ot)}
              >
                Dời sang hôm nay →
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Date Navigation Bar */}
      <div className="daily-date-bar">
        <div className="date-nav-buttons">
          <button
            type="button"
            className="button button--secondary button--sm"
            onClick={() => setSelectedDate(offsetDate(selectedDate, -1))}
          >
            ← Hôm trước
          </button>
          <button
            type="button"
            className={`button button--sm ${isToday ? 'button--primary' : 'button--secondary'}`}
            onClick={() => setSelectedDate(todayStr)}
          >
            Hôm nay
          </button>
          <button
            type="button"
            className="button button--secondary button--sm"
            onClick={() => setSelectedDate(offsetDate(selectedDate, 1))}
          >
            Hôm sau →
          </button>
        </div>

        <div className="date-display-group">
          <h2 className="current-date-heading">{formatDayOfWeekAndDate(selectedDate)}</h2>
          <input
            type="date"
            className="date-picker-input"
            value={selectedDate}
            onChange={e => e.target.value && setSelectedDate(e.target.value)}
            aria-label="Chọn ngày cụ thể"
          />
        </div>
      </div>

      {/* Daily Metrics Card */}
      <div className="daily-metrics-card">
        <div className="metric-box">
          <div className="metric-label">TỔNG THỜI LƯỢNG HỌC</div>
          <div className="metric-value">
            {tasksData.totalEstimatedMinutes} <span>phút</span>
          </div>
          <div className="metric-hint">
            {tasksData.totalEstimatedMinutes <= 180 ? (
              <span className="badge badge--success">✓ Phù hợp quỹ thời gian</span>
            ) : (
              <span className="badge badge--warning">⚠️ Cần cân nhắc chia nhỏ</span>
            )}
          </div>
        </div>

        <div className="metric-divider" />

        <div className="metric-box">
          <div className="metric-label">TIẾN ĐỘ HOÀN THÀNH</div>
          <div className="metric-value">
            {tasksData.completedCount} / {tasksData.totalCount} <span>việc</span>
          </div>
          <div className="progress-container">
            <div className="progress-bar-fill" style={{ width: `${percentComplete}%` }} />
          </div>
          <div className="metric-hint">
            <Badge variant="success">{tasksData.completedCount} hoàn thành</Badge>{' '}
            <Badge variant="outline">{tasksData.remainingCount} còn lại</Badge>
          </div>
        </div>

        <div className="metric-divider" />

        <div className="metric-box">
          <div className="metric-label">MỤC TIÊU LIÊN QUAN</div>
          <div className="metric-value">
            {goals.length} <span>môn học</span>
          </div>
          <button
            type="button"
            className="button-link"
            onClick={onNavigateGoals}
          >
            Xem mục tiêu ôn tập →
          </button>
        </div>
      </div>

      {/* Main Task List */}
      <div className="daily-tasks-section">
        <div className="tasks-section-header">
          <h2>Danh sách công việc ngày {selectedDate}</h2>
          <span className="tasks-count-pill">{tasksData.items.length} công việc</span>
        </div>

        {loading ? (
          <div className="tasks-loading">Đang tải lịch học…</div>
        ) : tasksData.items.length === 0 ? (
          <div className="tasks-empty">
            <span className="empty-icon">✦</span>
            <h3>Chưa có công việc nào cho ngày này</h3>
            <p>Hãy thêm công việc thủ công hoặc tạo kế hoạch ôn tập từ mục tiêu để bắt đầu.</p>
            <button
              type="button"
              className="button button--primary button--sm"
              onClick={() => { setEditingTask(null); setTaskDate(selectedDate); setNewTaskForm({ goalId: goals[0]?.id || '', title: '', estimatedMinutes: 60 }); setShowAddModal(true) }}
            >
              + Thêm công việc ngay
            </button>
          </div>
        ) : (
          <div className="tasks-list">
            {tasksData.items.map(task => (
              <div
                key={task.id}
                className={`task-row ${task.completed ? 'task-row--completed' : ''}`}
              >
                <Checkbox
                  checked={Boolean(task.completed)}
                  disabled={pendingIds.has(task.id)}
                  onCheckedChange={next => handleToggle(task, next)}
                  aria-label={`${task.completed ? 'Mở lại' : 'Đánh dấu hoàn thành'}: ${task.title}`}
                />

                <div className="task-body">
                  <div className="task-title-row">
                    <span className="task-title">{task.title}</span>
                    <span className={`task-source-badge ${task.source === 'ai' ? 'badge--ai' : 'badge--manual'}`}>
                      {task.source === 'ai' ? '✦ AI đề xuất' : 'Thủ công'}
                    </span>
                  </div>
                  <div className="task-meta">
                    <span className="task-subject-tag">{subjectByGoal[task.goalId] || 'Mục tiêu ôn tập'}</span>
                    <span className="task-duration">⏱️ {task.estimatedMinutes} phút</span>
                  </div>
                </div>

                <div className="task-actions">
                  <button type="button" className="button-link" disabled={pendingIds.has(task.id)} onClick={() => {
                    setEditingTask(task); setTaskDate(task.scheduledDate)
                    setNewTaskForm({ goalId: task.goalId, title: task.title, estimatedMinutes: task.estimatedMinutes })
                    setShowAddModal(true)
                  }}>Sửa</button>
                  <button
                    type="button"
                    className="task-delete-btn"
                    title="Xóa công việc"
                    onClick={() => handleDelete(task)}
                  >
                    ✕
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Quick Add Task Modal */}
      {showAddModal && (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal-card">
            <div className="modal-header">
              <h3>{editingTask ? 'Chỉnh sửa công việc' : 'Thêm công việc thủ công'}</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowAddModal(false)}
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleCreateTask}>
              <div className="modal-field">
                <label htmlFor="task-date">Ngày học</label>
                <input id="task-date" className="modal-input" type="date" value={taskDate} required onChange={e => setTaskDate(e.target.value)} />
              </div>
              <div className="modal-field">
                <label htmlFor="task-goal-select">Thuộc môn học / mục tiêu</label>
                <select
                  id="task-goal-select" disabled={Boolean(editingTask)}
                  className="modal-input"
                  value={newTaskForm.goalId}
                  onChange={e => setNewTaskForm(f => ({ ...f, goalId: e.target.value }))}
                >
                  {goals.length > 0 ? (
                    goals.map(g => (
                      <option key={g.id} value={g.id}>{g.subject}</option>
                    ))
                  ) : (
                    <option value="default-goal">Mục tiêu chung</option>
                  )}
                </select>
              </div>

              <div className="modal-field">
                <label htmlFor="task-title-input">Tên công việc</label>
                <input
                  id="task-title-input"
                  className="modal-input"
                  placeholder="Ví dụ: Ôn tập 10 câu trắc nghiệm SQL"
                  maxLength={200}
                  required
                  value={newTaskForm.title}
                  onChange={e => setNewTaskForm(f => ({ ...f, title: e.target.value }))}
                />
              </div>

              <div className="modal-field">
                <label htmlFor="task-duration-input">Thời lượng dự kiến (phút)</label>
                <input
                  id="task-duration-input"
                  className="modal-input"
                  type="number"
                  min="15"
                  max="480"
                  required
                  value={newTaskForm.estimatedMinutes}
                  onChange={e => setNewTaskForm(f => ({ ...f, estimatedMinutes: e.target.value }))}
                />
              </div>

              <div className="modal-field">
                <label>Ngày thực hiện</label>
                <input
                  className="modal-input"
                  type="date"
                  value={selectedDate}
                  disabled
                />
                <small className="field-hint">Công việc sẽ được thêm vào ngày đang xem ({selectedDate}).</small>
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
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Đang lưu…' : 'Thêm công việc'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
