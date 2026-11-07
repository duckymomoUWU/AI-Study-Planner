import { useCallback, useEffect, useState } from 'react'
import { createGoal, deleteGoal, GoalApiError, getGoal, listGoals, updateGoal } from '../lib/goals.js'
import { describeGoalError } from '../lib/goalErrors.js'
import { confirmAndDeleteGoal } from '../lib/goalDelete.js'
import GoalProgress from '../components/GoalProgress.jsx'
import './GoalsPage.css'

const emptyForm = {
  subject: '',
  topics: '',
  startDate: '',
  examDate: '',
  minutesPerDay: '90',
}

function localDateString(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function nextDate(value) {
  if (!value) return ''
  const date = new Date(`${value}T12:00:00`)
  date.setDate(date.getDate() + 1)
  return localDateString(date)
}

function maxDate90Days(startDateStr) {
  if (!startDateStr) return ''
  const date = new Date(`${startDateStr}T12:00:00`)
  date.setDate(date.getDate() + 90)
  return localDateString(date)
}

function displayDate(value) {
  if (!value) return 'Chưa có ngày'
  return new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T12:00:00`))
}

function fieldMessages(formErrors, name) {
  const value = formErrors[name]
  if (!value) return []
  return Array.isArray(value) ? value : [value]
}

function FieldError({ formErrors, name }) {
  const messages = fieldMessages(formErrors, name)
  return messages.map(message => <small className="field-error" key={message}>{message}</small>)
}

export default function GoalsPage({ onBack, onNavigateDaily }) {
  const [goals, setGoals] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState(null)
  const [pageError, setPageError] = useState('')
  const [formErrors, setFormErrors] = useState({})
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [successMessage, setSuccessMessage] = useState('')
  const [isUnauthenticated, setIsUnauthenticated] = useState(false)
  const [conflicts, setConflicts] = useState([])
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const refresh = useCallback(async () => {
    setLoading(true)
    setPageError('')
    setIsUnauthenticated(false)
    try {
      const items = await listGoals()
      setGoals(items)
    } catch (error) {
      if (error instanceof GoalApiError && error.status === 401) {
        setIsUnauthenticated(true)
      } else {
        setPageError(describeGoalError(error).message)
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    listGoals({ signal: controller.signal })
      .then(items => {
        if (!controller.signal.aborted) setGoals(items)
      })
      .catch(error => {
        if (!controller.signal.aborted && error.name !== 'AbortError') {
          if (error instanceof GoalApiError && error.status === 401) {
            setIsUnauthenticated(true)
          } else {
            setPageError(describeGoalError(error).message)
          }
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [])

  function changeField(event) {
    const { name, value } = event.target
    setForm(current => ({ ...current, [name]: value }))
    setFormErrors(current => ({ ...current, [name]: '' }))
  }

  function startEditing(goal) {
    setEditing(goal)
    setForm({
      subject: goal.subject || '',
      topics: goal.topics || '',
      startDate: goal.startDate || '',
      examDate: goal.examDate || '',
      minutesPerDay: String(goal.minutesPerDay ?? 90),
    })
    setFormErrors({})
    setConflicts([])
    setPageError('')
    setSuccessMessage('')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function resetForm() {
    setEditing(null)
    setForm(emptyForm)
    setFormErrors({})
    setConflicts([])
  }

  async function showDetail(goal) {
    setDetailLoading(true)
    setPageError('')
    try {
      setDetail(await getGoal(goal.id))
    } catch (error) {
      setDetail(null)
      const described = describeGoalError(error)
      if (error instanceof GoalApiError && error.status === 401) setIsUnauthenticated(true)
      setPageError(described.message)
    } finally {
      setDetailLoading(false)
    }
  }

  async function submitForm(event) {
    event.preventDefault()
    setSaving(true)
    setPageError('')
    setSuccessMessage('')
    setFormErrors({})
    setConflicts([])

    // Client-side 90 days guard
    if (form.startDate && form.examDate) {
      const start = new Date(`${form.startDate}T12:00:00`)
      const exam = new Date(`${form.examDate}T12:00:00`)
      const diffDays = Math.round((exam - start) / (1000 * 60 * 60 * 24))
      if (diffDays > 90) {
        setFormErrors(prev => ({ ...prev, examDate: 'Khoảng cách giữa ngày bắt đầu và ngày thi không được vượt quá 90 ngày.' }))
        setSaving(false)
        return
      }
    }

    const payload = {
      subject: form.subject,
      topics: form.topics,
      startDate: form.startDate,
      examDate: form.examDate,
      minutesPerDay: Number(form.minutesPerDay),
    }

    try {
      if (editing) {
        const updated = await updateGoal(editing.id, payload, editing.version)
        if (detail?.id === updated.id) setDetail(updated)
        setSuccessMessage('Đã cập nhật mục tiêu thành công!')
      } else {
        await createGoal(payload)
        setSuccessMessage('Đã tạo mục tiêu ôn tập thành công!')
      }
      setTimeout(() => setSuccessMessage(''), 4000)
      resetForm()
      await refresh()
    } catch (error) {
      const described = describeGoalError(error)
      setFormErrors(described.fieldErrors)
      setConflicts(described.conflicts)
      setPageError(described.message)
    } finally {
      setSaving(false)
    }
  }

  async function removeGoal(goal) {
    if (deletingId) return // a delete is already in flight

    setPageError('')
    setSuccessMessage('')
    setDeletingId(goal.id)
    try {
      const result = await confirmAndDeleteGoal(goal, {
        confirm: message => window.confirm(message),
        remove: deleteGoal,
      })
      if (result === 'cancelled') return
      setSuccessMessage(`Đã xóa mục tiêu "${goal.subject}".`)
      setTimeout(() => setSuccessMessage(''), 3000)
      if (editing?.id === goal.id) resetForm()
      if (detail?.id === goal.id) setDetail(null)
      await refresh()
    } catch (error) {
      const described = describeGoalError(error)
      setPageError(described.message)
      if (error instanceof GoalApiError && error.status === 404) {
        await refresh() // already removed elsewhere: bring the list back in sync
      }
    } finally {
      setDeletingId(null)
    }
  }

  const examMin = form.startDate
    ? [localDateString(), nextDate(form.startDate)].sort().at(-1)
    : localDateString()
  const examMax = form.startDate ? maxDate90Days(form.startDate) : ''

  return (
    <section className="goals-page" aria-labelledby="goals-title">
      <div className="goals-heading">
        <div>
          <p className="eyebrow">Mục tiêu</p>
          <h1 id="goals-title">Mục tiêu ôn tập</h1>
          <p>Tạo mục tiêu rõ ràng để giới hạn thời gian và chuẩn bị cho kỳ thi.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button className="button button--secondary" type="button" onClick={onBack}>← Tổng quan</button>
          {onNavigateDaily && (
            <button className="button button--primary" type="button" onClick={onNavigateDaily}>Xem lịch học hôm nay →</button>
          )}
        </div>
      </div>

      {/* Feedback Alert */}
      {successMessage && (
        <div style={{
          background: '#ecfdf5',
          border: '1px solid #a7f3d0',
          color: '#065f46',
          padding: '0.75rem 1rem',
          borderRadius: '8px',
          fontSize: '0.875rem',
        }}>
          {successMessage}
        </div>
      )}

      {/* Unauthenticated Alert */}
      {isUnauthenticated && (
        <div style={{
          background: '#eff6ff',
          border: '1px solid #bfdbfe',
          color: '#1e40af',
          padding: '0.75rem 1rem',
          borderRadius: '8px',
          fontSize: '0.875rem',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div><strong>Chưa xác thực:</strong> Bạn cần đăng nhập để lưu trữ mục tiêu cá nhân.</div>
          <button
            type="button"
            className="button button--primary"
            style={{ fontSize: '0.8125rem', padding: '0.35rem 0.75rem' }}
            onClick={() => { window.location.hash = '#login' }}
          >
            Đăng nhập ngay →
          </button>
        </div>
      )}

      {pageError && (
        <div className="goal-alert" role="alert">
          {pageError}
          {conflicts.length > 0 && (
            <ul className="goal-conflicts">
              {conflicts.map(message => <li key={message}>{message}</li>)}
            </ul>
          )}
        </div>
      )}

      <div className="goals-layout">
        <section className="goal-form-card" aria-labelledby="goal-form-title">
          <div className="goal-card-heading">
            <h2 id="goal-form-title">{editing ? 'Chỉnh sửa mục tiêu' : 'Tạo mục tiêu mới'}</h2>
            <span>{editing ? `Phiên bản v${editing.version}` : 'Mục tiêu mới'}</span>
          </div>
          <form onSubmit={submitForm} noValidate>
            <div className="goal-field">
              <label htmlFor="goal-subject">Môn học</label>
              <input id="goal-subject" name="subject" value={form.subject} onChange={changeField}
                maxLength={100} placeholder="Ví dụ: Cơ sở dữ liệu" required aria-invalid={Boolean(formErrors.subject)} />
              <FieldError formErrors={formErrors} name="subject" />
            </div>
            <div className="goal-field">
              <label htmlFor="goal-topics">Chủ đề cần ôn tập</label>
              <textarea id="goal-topics" name="topics" value={form.topics} onChange={changeField}
                rows={3} placeholder="Ví dụ: SQL, chuẩn hóa, transaction..." required
                aria-invalid={Boolean(formErrors.topics)} />
              <FieldError formErrors={formErrors} name="topics" />
            </div>
            <div className="goal-date-grid">
              <div className="goal-field">
                <label htmlFor="goal-start">Ngày bắt đầu</label>
                <input id="goal-start" type="date" name="startDate" value={form.startDate}
                  onChange={changeField} required aria-invalid={Boolean(formErrors.startDate)} />
                <FieldError formErrors={formErrors} name="startDate" />
              </div>
              <div className="goal-field">
                <label htmlFor="goal-exam">Ngày thi</label>
                <input id="goal-exam" type="date" name="examDate" value={form.examDate}
                  min={examMin} max={examMax || undefined} onChange={changeField} required aria-invalid={Boolean(formErrors.examDate)} />
                <small className="field-hint">Tối đa 90 ngày kể từ ngày bắt đầu.</small>
                <FieldError formErrors={formErrors} name="examDate" />
              </div>
            </div>
            <div className="goal-field">
              <label htmlFor="goal-budget">Thời gian học mỗi ngày</label>
              <div className="goal-budget-input">
                <input id="goal-budget" type="number" name="minutesPerDay" value={form.minutesPerDay}
                  onChange={changeField} min="15" max="480" step="1" required
                  aria-invalid={Boolean(formErrors.minutesPerDay)} />
                <span>phút / ngày</span>
              </div>
              <small className="field-hint">Cho phép từ 15 đến 480 phút mỗi ngày.</small>
              <FieldError formErrors={formErrors} name="minutesPerDay" />
            </div>
            <div className="goal-form-actions">
              <button className="button button--primary" type="submit" disabled={saving}>
                {saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tạo mục tiêu'}
              </button>
              {editing && <button className="button button--secondary" type="button" onClick={resetForm} disabled={saving}>Hủy sửa</button>}
            </div>
          </form>
        </section>

        <section className="goal-list-card" aria-labelledby="goal-list-title">
          <div className="goal-card-heading">
            <h2 id="goal-list-title">Mục tiêu của tôi</h2>
            <span>{goals.length} mục tiêu</span>
          </div>
          {detail && (
            <section className="goal-detail" aria-label="Chi tiết mục tiêu">
              <div className="goal-item-top">
                <h3>{detail.subject}</h3>
                <button type="button" onClick={() => setDetail(null)}>Đóng</button>
              </div>
              <dl>
                <dt>Chủ đề</dt><dd>{detail.topics}</dd>
                <dt>Ngày bắt đầu</dt><dd>{displayDate(detail.startDate)}</dd>
                <dt>Ngày thi</dt><dd>{displayDate(detail.examDate)}</dd>
                <dt>Thời gian mỗi ngày</dt><dd>{detail.minutesPerDay} phút</dd>
                <dt>Múi giờ</dt><dd>{detail.timezone}</dd>
                <dt>Phiên bản</dt><dd>v{detail.version}</dd>
              </dl>
              <GoalProgress goalId={detail.id} version={detail.version} />
            </section>
          )}
          {loading ? (
            <p className="goal-empty" role="status">Đang tải mục tiêu…</p>
          ) : goals.length === 0 ? (
            <div className="goal-empty">
              <span aria-hidden="true">✦</span>
              <h3>Chưa có mục tiêu nào</h3>
              <p>Tạo mục tiêu đầu tiên để bắt đầu lập kế hoạch ôn tập.</p>
            </div>
          ) : (
            <div className="goal-list">
              {goals.map(goal => (
                <article className="goal-item" key={goal.id}>
                  <div className="goal-item-top">
                    <div><h3>{goal.subject}</h3><span className="goal-version">v{goal.version}</span></div>
                    <span className="goal-budget">{goal.minutesPerDay} phút/ngày</span>
                  </div>
                  <p className="goal-topics">{goal.topics}</p>
                  <p className="goal-dates">Bắt đầu {displayDate(goal.startDate)} · Thi {displayDate(goal.examDate)}</p>
                  <GoalProgress goalId={goal.id} version={goal.version} />
                  <div className="goal-item-actions">
                    <button type="button" onClick={() => showDetail(goal)} disabled={detailLoading}>Chi tiết</button>
                    <button type="button" onClick={() => startEditing(goal)}>Chỉnh sửa</button>
                    <button type="button" className="goal-delete" onClick={() => removeGoal(goal)}
                      disabled={deletingId !== null}>
                      {deletingId === goal.id ? 'Đang xóa…' : 'Xóa'}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
      <p className="goal-page-note">Dữ liệu lấy từ máy chủ cho tài khoản đang đăng nhập; máy chủ kiểm tra quyền sở hữu.</p>
    </section>
  )
}
