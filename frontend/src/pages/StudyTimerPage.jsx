import { useCallback, useEffect, useRef, useState } from 'react'
import {
  actOnSession,
  calculateRemainingSeconds,
  formatTime,
  generateIdempotencyKey,
  getActiveSession,
  listStudySessions,
  startSession,
} from '../lib/studySessions.js'
import { listTasksByDate } from '../lib/tasks.js'
import { listGoals } from '../lib/goals.js'
import { vietnamToday } from '../lib/goalPlanning.js'
import './StudyTimerPage.css'

export default function StudyTimerPage({ currentUser, initialTaskId, onNavigateAuth, onNavigateGoals }) {
  // Session & Clock State
  const [session, setSession] = useState(null)
  const [displayRemaining, setDisplayRemaining] = useState(1500)
  const [snapshotTime, setSnapshotTime] = useState(() => Date.now())
  const [loading, setLoading] = useState(() => Boolean(currentUser))
  const [error, setError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [revision, setRevision] = useState(0)

  // Screen Reader polite announcement
  const [announcement, setAnnouncement] = useState('')

  // Task selection state for starting new session
  const [availableTasks, setAvailableTasks] = useState([])
  const [selectedTaskId, setSelectedTaskId] = useState(() => initialTaskId || '')

  // Modals & Sub-states
  const [showCancelModal, setShowCancelModal] = useState(false)
  const [recentSessions, setRecentSessions] = useState([])

  // Break mode state (5 minutes = 300 seconds UI countdown)
  const [isBreakActive, setIsBreakActive] = useState(false)
  const [breakRemaining, setBreakRemaining] = useState(300)
  const [isBreakRunning, setIsBreakRunning] = useState(false)

  // Stable references for idempotency keys
  const finishKeyRef = useRef(generateIdempotencyKey())
  const startKeyRef = useRef(generateIdempotencyKey())
  const autoFinishTriggeredRef = useRef(false)

  useEffect(() => {
    document.title = 'Phiên học — AI Study Planner'
  }, [])

  // Sync state with server
  const syncActiveSession = useCallback(async () => {
    if (!currentUser) return
    try {
      const active = await getActiveSession()
      if (active) {
        setSession(active)
        const now = Date.now()
        setSnapshotTime(now)
        const rem = calculateRemainingSeconds(active, now, now)
        setDisplayRemaining(rem)
        autoFinishTriggeredRef.current = active.focusComplete || rem <= 0
      } else {
        setSession(null)
      }
    } catch {
      // Non-fatal background sync
    }
  }, [currentUser])

  // Load recent history
  const loadHistory = useCallback(async () => {
    if (!currentUser) return
    try {
      const history = await listStudySessions({ page: 1, limit: 5 })
      if (Array.isArray(history)) {
        setRecentSessions(history)
      } else if (history && Array.isArray(history.data)) {
        setRecentSessions(history.data)
      }
    } catch {
      // Non-fatal
    }
  }, [currentUser])

  // Initial mount / revision sync
  useEffect(() => {
    if (!currentUser) return
    const controller = new AbortController()
    let ignore = false

    async function sync() {
      try {
        const active = await getActiveSession({ signal: controller.signal })
        if (ignore) return
        setError('')
        if (active) {
          setSession(active)
          const now = Date.now()
          setSnapshotTime(now)
          const rem = calculateRemainingSeconds(active, now, now)
          setDisplayRemaining(rem)
          autoFinishTriggeredRef.current = active.focusComplete || rem <= 0
        } else {
          setSession(null)
        }
      } catch (err) {
        if (ignore || err.name === 'AbortError') return
        if (err.status === 401) {
          setError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
        } else {
          setError(err.message || 'Không thể kiểm tra phiên học đang chạy.')
        }
      }

      try {
        const today = vietnamToday()
        const [dayData, goalsData] = await Promise.all([
          listTasksByDate(today, { signal: controller.signal }).catch(() => ({ items: [] })),
          listGoals({ signal: controller.signal }).catch(() => []),
        ])
        if (ignore) return
        const goalSubjectMap = {}
        for (const g of goalsData) {
          goalSubjectMap[g.id] = g.subject || g.title
        }
        const tasks = (dayData?.items || []).map(t => ({
          ...t,
          subject: goalSubjectMap[t.goalId] || 'Môn học',
        }))
        setAvailableTasks(tasks)
        if (initialTaskId) {
          setSelectedTaskId(initialTaskId)
        } else if (tasks.length > 0) {
          setSelectedTaskId(prev => prev || tasks[0].id)
        }
      } catch {
        // Non-fatal
      }

      try {
        const history = await listStudySessions({ page: 1, limit: 5 }, { signal: controller.signal })
        if (ignore) return
        if (Array.isArray(history)) {
          setRecentSessions(history)
        } else if (history && Array.isArray(history.data)) {
          setRecentSessions(history.data)
        }
      } catch {
        // Non-fatal
      } finally {
        if (!ignore) setLoading(false)
      }
    }

    sync()

    return () => {
      ignore = true
      controller.abort()
    }
  }, [currentUser, revision, initialTaskId])

  // Background re-sync on visibility change / window focus (TUI-03 & TUI-07)
  useEffect(() => {
    function handleVisibilityOrFocus() {
      if (document.visibilityState === 'visible' && currentUser) {
        syncActiveSession()
        loadHistory()
      }
    }

    document.addEventListener('visibilitychange', handleVisibilityOrFocus)
    window.addEventListener('focus', handleVisibilityOrFocus)
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityOrFocus)
      window.removeEventListener('focus', handleVisibilityOrFocus)
    }
  }, [currentUser, syncActiveSession, loadHistory])

  // Auto-Finish when timer reaches 00:00 (TUI-04)
  const handleAutoFinish = useCallback(async () => {
    if (!session || session.status !== 'RUNNING') return
    setIsSubmitting(true)
    setSaveError('')
    setAnnouncement('Đã hoàn thành 25 phút tập trung. Đang lưu kết quả vào máy chủ...')

    try {
      const updated = await actOnSession(session.id, 'FINISH', session.version, finishKeyRef.current)
      setSession(updated)
      setDisplayRemaining(0)
      setAnnouncement('Đã hoàn thành phiên học 25 phút và lưu thành công vào máy chủ.')
      loadHistory()
    } catch (err) {
      setSaveError(
        err.message || 'Chưa lưu được kết quả phiên học vào máy chủ do sự cố kết nối. Vui lòng bấm thử lại.'
      )
      setAnnouncement('Chưa lưu được kết quả phiên học vào máy chủ.')
    } finally {
      setIsSubmitting(false)
    }
  }, [session, loadHistory])

  // Monotonic Tick for RUNNING session (TUI-03 & TUI-04)
  useEffect(() => {
    if (!session || session.status !== 'RUNNING') return

    const interval = setInterval(() => {
      const now = Date.now()
      const rem = calculateRemainingSeconds(session, now, snapshotTime)
      setDisplayRemaining(rem)

      if (rem <= 0 && !autoFinishTriggeredRef.current) {
        autoFinishTriggeredRef.current = true
        clearInterval(interval)
        handleAutoFinish()
      }
    }, 500)

    return () => clearInterval(interval)
  }, [session, snapshotTime, handleAutoFinish])

  // Break Clock Tick
  useEffect(() => {
    if (!isBreakActive || !isBreakRunning) return
    const interval = setInterval(() => {
      setBreakRemaining(prev => {
        if (prev <= 1) {
          clearInterval(interval)
          setIsBreakRunning(false)
          setAnnouncement('Đã hoàn thành 5 phút nghỉ giải lao.')
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(interval)
  }, [isBreakActive, isBreakRunning])

  // Retry Auto-Finish with the exact same Idempotency-Key
  async function handleRetryFinish() {
    if (!session) return
    setIsSubmitting(true)
    setSaveError('')
    try {
      const updated = await actOnSession(session.id, 'FINISH', session.version, finishKeyRef.current)
      setSession(updated)
      setDisplayRemaining(0)
      setAnnouncement('Đã lưu kết quả phiên học vào máy chủ thành công.')
      loadHistory()
    } catch (err) {
      setSaveError(err.message || 'Chưa lưu được kết quả. Vui lòng kiểm tra kết nối mạng và thử lại.')
    } finally {
      setIsSubmitting(false)
    }
  }

  // Start new session (TUI-01, TUI-02)
  async function handleStartSession(e) {
    e.preventDefault()
    if (!selectedTaskId) {
      setError('Vui lòng chọn một công việc thật để bắt đầu học.')
      return
    }
    if (isSubmitting) return

    setIsSubmitting(true)
    setError('')
    setSaveError('')
    startKeyRef.current = generateIdempotencyKey()
    finishKeyRef.current = generateIdempotencyKey()
    autoFinishTriggeredRef.current = false

    try {
      const newSession = await startSession(selectedTaskId, startKeyRef.current)
      setSession(newSession)
      const now = Date.now()
      setSnapshotTime(now)
      setDisplayRemaining(calculateRemainingSeconds(newSession, now, now))
      setIsBreakActive(false)
      setAnnouncement('Phiên học 25 phút đã bắt đầu.')
    } catch (err) {
      if (err.status === 409 && err.code === 'SESSION_ALREADY_ACTIVE') {
        setError('Bạn đã có phiên học đang diễn ra. Đang đồng bộ phiên hiện tại...')
        setRevision(r => r + 1)
      } else if (err.status === 401) {
        setError('Phiên đăng nhập đã hết hạn.')
      } else {
        setError(err.message || 'Không thể bắt đầu phiên học.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  // Handle Action: PAUSE, RESUME, FINISH early, CANCEL (TUI-03, TUI-05, TUI-07)
  async function handleAction(action) {
    if (!session || isSubmitting) return
    setIsSubmitting(true)
    setError('')
    setSaveError('')

    const actionKey = generateIdempotencyKey()
    try {
      const updated = await actOnSession(session.id, action, session.version, actionKey)
      setSession(updated)
      const now = Date.now()
      setSnapshotTime(now)
      const rem = calculateRemainingSeconds(updated, now, now)
      setDisplayRemaining(rem)

      if (action === 'PAUSE') {
        setAnnouncement('Đã tạm dừng phiên học.')
      } else if (action === 'RESUME') {
        setAnnouncement('Tiếp tục phiên học.')
      } else if (action === 'FINISH') {
        setAnnouncement('Đã kết thúc sớm phiên học.')
        loadHistory()
      } else if (action === 'CANCEL') {
        setAnnouncement('Đã hủy phiên học. Thời gian không được tính vào tổng tích lũy.')
        setSession(null)
        loadHistory()
      }
    } catch (err) {
      if (err.status === 409) {
        if (err.code === 'SESSION_VERSION_CONFLICT') {
          setError('Phiên học đã được thay đổi từ một cửa sổ khác. Đang làm mới dữ liệu...')
        } else if (err.code === 'SESSION_STATE_CONFLICT') {
          setError('Trạng thái phiên học đã thay đổi. Đang làm mới dữ liệu...')
        } else {
          setError(err.message || 'Xung đột phiên học.')
        }
        setRevision(r => r + 1)
      } else {
        setError(err.message || 'Không thể thực hiện thao tác.')
      }
    } finally {
      setIsSubmitting(false)
      setShowCancelModal(false)
    }
  }

  // Start 5-minute break
  function handleStartBreak() {
    setIsBreakActive(true)
    setBreakRemaining(300)
    setIsBreakRunning(true)
    setAnnouncement('Bắt đầu 5 phút nghỉ giải lao thư giãn.')
  }

  // Reset to study another task
  function handleStudyAnother() {
    setSession(null)
    setIsBreakActive(false)
    setError('')
    setSaveError('')
    setRevision(r => r + 1)
  }

  // Auth requirement guard
  if (!currentUser) {
    return (
      <section className="timer-page" aria-labelledby="timer-auth-title">
        <header className="timer-header">
          <h1 id="timer-auth-title">Phiên học</h1>
          <p>Đăng nhập để theo dõi và lưu trữ thời gian học tập trung thực tế.</p>
        </header>
        <div className="timer-card">
          <p>Bạn cần đăng nhập tài khoản để khởi tạo và lưu trữ phiên học.</p>
          <button
            type="button"
            className="timer-btn timer-btn--primary"
            onClick={onNavigateAuth}
          >
            Đăng nhập ngay
          </button>
        </div>
      </section>
    )
  }

  const isCompleted = session && (session.status === 'COMPLETED' || session.focusComplete)
  const isRunning = session && session.status === 'RUNNING' && !isCompleted
  const isPaused = session && session.status === 'PAUSED' && !isCompleted

  const plannedSec = session?.plannedFocusSeconds || 1500
  const progressPercent = Math.min(100, Math.max(0, Math.round(((plannedSec - displayRemaining) / plannedSec) * 100)))

  return (
    <section className="timer-page" aria-labelledby="timer-page-title">
      {/* Polite Screen Reader Announcement */}
      <div role="status" aria-live="polite" className="timer-sr-only">
        {announcement}
      </div>

      <header className="timer-header">
        <h1 id="timer-page-title">Phiên học</h1>
        <p>Học tập trung 25 phút. Thời gian được đo đếm chuẩn xác trên máy chủ.</p>
      </header>

      {/* Global Errors or Alerts */}
      {error && (
        <div className="timer-alert timer-alert--error" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="timer-btn"
            style={{ minHeight: '36px', padding: '6px 14px', fontSize: '13px' }}
            onClick={() => setRevision(r => r + 1)}
          >
            Thử lại
          </button>
        </div>
      )}

      {saveError && (
        <div className="timer-alert timer-alert--error" role="alert">
          <span>{saveError}</span>
          <button
            type="button"
            className="timer-btn timer-btn--primary"
            style={{ minHeight: '36px', padding: '6px 14px', fontSize: '13px' }}
            disabled={isSubmitting}
            onClick={handleRetryFinish}
          >
            {isSubmitting ? 'Đang gửi lại...' : 'Thử lại lưu kết quả'}
          </button>
        </div>
      )}

      {loading && <p role="status">Đang tải trạng thái phiên học...</p>}

      {/* Active or Paused Session View */}
      {!loading && session && (isRunning || isPaused) && (
        <article className="timer-card">
          <div className="timer-card-header">
            <div className="timer-task-info">
              <span className="timer-subject-badge">{session.subject || 'Môn học'}</span>
              <h2>{session.taskTitle || 'Công việc học tập'}</h2>
            </div>
            <div>
              {isRunning && (
                <span className="timer-status-pill timer-status-pill--running">
                  🟢 ĐANG TẬP TRUNG (RUNNING)
                </span>
              )}
              {isPaused && (
                <span className="timer-status-pill timer-status-pill--paused">
                  ⏸️ ĐÃ TẠM DỪNG (PAUSED)
                </span>
              )}
            </div>
          </div>

          <div className="timer-clock-section">
            <div
              className={`timer-clock-display ${isPaused ? 'timer-clock-display--paused' : ''}`}
              aria-hidden="true"
            >
              {formatTime(displayRemaining)}
            </div>
            <p className="timer-clock-desc">
              {isRunning && 'Hãy duy trì sự tập trung cho đến khi đồng hồ đếm về 00:00.'}
              {isPaused && 'Phiên học đang tạm dừng. Thời gian dừng không được tính vào tổng học.'}
            </p>

            <div
              className="timer-progress-bar-wrap"
              role="progressbar"
              aria-valuenow={progressPercent}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Tiến độ 25 phút tập trung"
            >
              <div className="timer-progress-bar-fill" style={{ width: `${progressPercent}%` }} />
            </div>
          </div>

          <div className="timer-actions-row">
            {isRunning && (
              <button
                type="button"
                className="timer-btn"
                disabled={isSubmitting}
                onClick={() => handleAction('PAUSE')}
              >
                ⏸ Tạm dừng
              </button>
            )}

            {isPaused && (
              <button
                type="button"
                className="timer-btn timer-btn--primary"
                disabled={isSubmitting}
                onClick={() => handleAction('RESUME')}
              >
                ▶ Tiếp tục học
              </button>
            )}

            <button
              type="button"
              className="timer-btn"
              disabled={isSubmitting}
              onClick={() => handleAction('FINISH')}
              title="Lưu số giây thực tế đã học tính đến thời điểm này"
            >
              ⏹ Kết thúc sớm
            </button>

            <button
              type="button"
              className="timer-btn timer-btn--danger"
              disabled={isSubmitting}
              onClick={() => setShowCancelModal(true)}
            >
              ✕ Hủy phiên
            </button>
          </div>
        </article>
      )}

      {/* Completed Session View */}
      {!loading && session && isCompleted && (
        <article className="timer-card">
          <div className="timer-card-header">
            <div className="timer-task-info">
              <span className="timer-subject-badge">{session.subject || 'Môn học'}</span>
              <h2>{session.taskTitle}</h2>
            </div>
            <span className="timer-status-pill timer-status-pill--completed">
              ✓ ĐÃ HOÀN THÀNH (COMPLETED)
            </span>
          </div>

          <div className="timer-clock-section">
            <div className="timer-clock-display" aria-hidden="true">
              {formatTime(0)}
            </div>
            <p className="timer-clock-desc" style={{ color: '#137333', fontWeight: '500' }}>
              Tuyệt vời! Bạn đã hoàn thành phiên học tập trung.
            </p>
            <p className="timer-clock-desc">
              Thời gian học thực tế được ghi nhận: {Math.round((session.focusedSeconds || 1500) / 60)} phút ({session.focusedSeconds || 1500} giây).
            </p>
          </div>

          <div className="timer-actions-row">
            {!isBreakActive && (
              <button
                type="button"
                className="timer-btn timer-btn--primary"
                onClick={handleStartBreak}
              >
                ☕ Nghỉ giải lao 5 phút
              </button>
            )}
            <button
              type="button"
              className="timer-btn"
              onClick={handleStudyAnother}
            >
              Bắt đầu phiên học mới
            </button>
            <button
              type="button"
              className="timer-btn"
              onClick={onNavigateGoals}
            >
              Xem mục tiêu học tập ↗
            </button>
          </div>
        </article>
      )}

      {/* 5-minute Break View (TUI-06) */}
      {isBreakActive && (
        <article className="timer-card" style={{ borderLeft: '4px solid #7627bb' }}>
          <div className="timer-card-header">
            <div>
              <span className="timer-subject-badge" style={{ background: '#f3e8fd', color: '#7627bb' }}>
                Thư giãn & Tái tạo năng lượng
              </span>
              <h2>Nghỉ giải lao 5 phút (Pomodoro Break)</h2>
            </div>
            <span className="timer-status-pill timer-status-pill--break">
              ☕ NGHỈ GIẢI LAO
            </span>
          </div>

          <div className="timer-clock-section">
            <div className="timer-clock-display" aria-hidden="true">
              {formatTime(breakRemaining)}
            </div>
            <p className="timer-clock-desc">
              Uống một cốc nước, vươn vai và để mắt nghỉ ngơi trước khi bước vào phiên học tiếp theo.
            </p>
            <p style={{ fontSize: '12px', color: 'var(--muted)', marginTop: '8px' }}>
              * Thời gian nghỉ giải lao chạy độc lập trên giao diện, không tính vào tổng thời gian học tập.
            </p>
          </div>

          <div className="timer-actions-row">
            {!isBreakRunning ? (
              <button
                type="button"
                className="timer-btn timer-btn--primary"
                onClick={() => setIsBreakRunning(true)}
              >
                ▶ Bắt đầu nghỉ
              </button>
            ) : (
              <button
                type="button"
                className="timer-btn"
                onClick={() => setIsBreakRunning(false)}
              >
                ⏸ Tạm dừng nghỉ
              </button>
            )}

            <button
              type="button"
              className="timer-btn"
              onClick={() => {
                setBreakRemaining(300)
                setIsBreakRunning(false)
              }}
            >
              Đặt lại 5:00
            </button>

            <button
              type="button"
              className="timer-btn"
              onClick={() => setIsBreakActive(false)}
            >
              Bỏ qua nghỉ / Học tiếp
            </button>
          </div>
        </article>
      )}

      {/* No Active Session: Task Picker & Start (TUI-01, TUI-02) */}
      {!loading && !session && !isBreakActive && (
        <div className="timer-card">
          <h2>Chọn công việc để bắt đầu học tập</h2>
          <p style={{ color: 'var(--muted)', marginTop: '4px', marginBottom: '20px' }}>
            Mỗi phiên kéo dài 25 phút. Hãy tập trung giải quyết trọn vẹn một đầu việc cụ thể.
          </p>

          <form onSubmit={handleStartSession}>
            <div className="timer-task-selector">
              <label htmlFor="task-select" style={{ display: 'block', fontWeight: '600', marginBottom: '8px' }}>
                Công việc cần học hôm nay:
              </label>

              {availableTasks.length > 0 ? (
                <select
                  id="task-select"
                  className="timer-task-select"
                  value={selectedTaskId}
                  onChange={e => setSelectedTaskId(e.target.value)}
                  disabled={isSubmitting}
                >
                  {availableTasks.map(t => (
                    <option key={t.id} value={t.id}>
                      [{t.subject}] {t.title} ({t.estimatedMinutes} phút)
                    </option>
                  ))}
                </select>
              ) : initialTaskId ? (
                <div style={{ marginBottom: '16px', padding: '12px', background: 'var(--soft)', borderRadius: '12px' }}>
                  <span>Công việc đã chọn từ lịch (ID: {initialTaskId})</span>
                </div>
              ) : (
                <div style={{ marginBottom: '20px', padding: '16px', background: 'var(--soft)', borderRadius: '12px' }}>
                  <p style={{ margin: 0 }}>
                    Hôm nay chưa có công việc nào trong kế hoạch. Bạn có thể mở trang Mục tiêu để tạo môn học và lập lịch.
                  </p>
                  <button
                    type="button"
                    className="timer-btn"
                    style={{ marginTop: '12px', minHeight: '38px', padding: '8px 16px', fontSize: '13px' }}
                    onClick={onNavigateGoals}
                  >
                    Xem và tạo mục tiêu ↗
                  </button>
                </div>
              )}

              {(availableTasks.length > 0 || initialTaskId) && (
                <button
                  type="submit"
                  className="timer-btn timer-btn--primary"
                  disabled={isSubmitting || !selectedTaskId}
                >
                  {isSubmitting ? 'Đang khởi tạo...' : 'Bắt đầu học (25 phút) ↗'}
                </button>
              )}
            </div>
          </form>
        </div>
      )}

      {/* Recent Study History */}
      {recentSessions.length > 0 && (
        <section className="timer-history-section" aria-labelledby="history-title">
          <h3 id="history-title">Lịch sử phiên học gần đây</h3>
          <div className="timer-history-list">
            {recentSessions.map(s => {
              const dateStr = s.startedAt
                ? new Intl.DateTimeFormat('vi-VN', {
                  hour: '2-digit',
                  minute: '2-digit',
                  day: 'numeric',
                  month: 'numeric',
                }).format(new Date(s.startedAt))
                : ''

              return (
                <div key={s.id} className="timer-history-item">
                  <div className="timer-history-item-left">
                    <span className="timer-history-title">{s.taskTitle || 'Phiên học'}</span>
                    <span className="timer-history-meta">
                      {s.subject || 'Môn học'} {dateStr ? `· ${dateStr}` : ''}
                    </span>
                  </div>
                  <div className="timer-history-item-right">
                    <span className="timer-history-duration">
                      ⏱ {Math.round((s.focusedSeconds || 0) / 60)} phút ({s.focusedSeconds || 0}s)
                    </span>
                    <span
                      className={`timer-status-pill ${s.status === 'COMPLETED'
                          ? 'timer-status-pill--completed'
                          : s.status === 'CANCELLED'
                            ? 'timer-status-pill--paused'
                            : ''
                        }`}
                      style={{ fontSize: '12px', padding: '3px 10px' }}
                    >
                      {s.status === 'COMPLETED' ? 'Hoàn tất' : s.status === 'CANCELLED' ? 'Đã hủy' : s.status}
                    </span>
                  </div>
                </div>
              )
            })}
          </div>
        </section>
      )}

      {/* Confirmation Modal for Session Cancellation (TUI-05) */}
      {showCancelModal && (
        <div className="timer-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="cancel-modal-title">
          <div className="timer-modal">
            <h3 id="cancel-modal-title">Xác nhận hủy phiên học?</h3>
            <p>
              Bạn có chắc chắn muốn hủy phiên học này? Thời gian trong phiên này sẽ không được tính vào tổng thời gian học tập đã tích lũy.
            </p>
            <div className="timer-modal-actions">
              <button
                type="button"
                className="timer-btn"
                disabled={isSubmitting}
                onClick={() => setShowCancelModal(false)}
              >
                Quay lại học
              </button>
              <button
                type="button"
                className="timer-btn timer-btn--danger"
                disabled={isSubmitting}
                onClick={() => handleAction('CANCEL')}
              >
                {isSubmitting ? 'Đang hủy...' : 'Xác nhận hủy'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
