import { useCallback, useEffect, useState } from 'react'
import { createGoal, deleteGoal, GoalApiError, listGoals, updateGoal } from '../lib/goals.js'
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

function apiMessage(error) {
  if (error instanceof GoalApiError && error.status === 401) {
    return 'Bạn cần đăng nhập để quản lý mục tiêu cá nhân.'
  }
  if (error instanceof GoalApiError && error.status === 404) {
    return 'API mục tiêu chưa khả dụng hoặc mục tiêu không còn tồn tại. Backend Goal CRUD vẫn đang được hoàn thiện.'
  }
  if (error instanceof GoalApiError && (error.status === 502 || error.status === 503 || error.status === 504)) {
    return 'Máy chủ backend chưa khởi động (HTTP 502 Bad Gateway). Giao diện đang hoạt động với dữ liệu mô phỏng (Placeholder).'
  }
  if (error instanceof GoalApiError && error.status === 409) {
    return 'Mục tiêu vừa được cập nhật ở nơi khác. Hãy tải lại danh sách trước khi sửa tiếp.'
  }
  return error?.message || 'Đã xảy ra lỗi. Vui lòng thử lại.'
}

function mapFieldErrors(error) {
  return Object.fromEntries((error?.fieldErrors || []).map(item => [item.field, item.message]))
}

export default function GoalsPage({ currentUser, onBack, onNavigateDaily, onNavigateAuth }) {
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
        setPageError(apiMessage(error))
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
            setPageError(apiMessage(error))
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
    setPageError('')
    setSuccessMessage('')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function resetForm() {
    setEditing(null)
    setForm(emptyForm)
    setFormErrors({})
  }

  async function submitForm(event) {
    event.preventDefault()
    setSaving(true)
    setPageError('')
    setSuccessMessage('')
    setFormErrors({})

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
      ...(editing ? { version: editing.version } : {}),
    }

    try {
      if (editing) {
        await updateGoal(editing.id, payload)
        setSuccessMessage('Đã cập nhật mục tiêu thành công!')
      } else {
        await createGoal(payload)
        setSuccessMessage('Đã tạo mục tiêu ôn tập thành công!')
      }
      setTimeout(() => setSuccessMessage(''), 4000)
      resetForm()
      await refresh()
    } catch (error) {
      if (error instanceof GoalApiError && error.fieldErrors.length) {
        setFormErrors(mapFieldErrors(error))
      }
      setPageError(apiMessage(error))
    } finally {
      setSaving(false)
    }
  }

  async function removeGoal(goal) {
    const confirmed = window.confirm(
      `Bạn có chắc muốn xóa mục tiêu "${goal.subject}"? Các công việc và bản nháp kế hoạch liên quan cũng sẽ bị xóa.`,
    )
    if (!confirmed) return

    setDeletingId(goal.id)
    setPageError('')
    setSuccessMessage('')
    try {
      await deleteGoal(goal.id)
      setSuccessMessage(`Đã xóa mục tiêu "${goal.subject}".`)
      setTimeout(() => setSuccessMessage(''), 3000)
      if (editing?.id === goal.id) resetForm()
      await refresh()
    } catch (error) {
      setPageError(apiMessage(error))
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
        <div className="goals-heading-info">
          <p className="eyebrow">SPRINT 2 · US-01</p>
          <h1 id="goals-title">Mục tiêu ôn tập</h1>
          <p className="goals-subtitle">Tạo mục tiêu rõ ràng để giới hạn thời gian và chuẩn bị cho kỳ thi.</p>
        </div>
        <div className="goals-heading-actions">
          <button className="button button--secondary" type="button" onClick={onBack}>
            ← Tổng quan
          </button>
          {onNavigateDaily && (
            <button className="button button--primary" type="button" onClick={onNavigateDaily}>
              Xem lịch học hôm nay →
            </button>
          )}
        </div>
      </div>

      {/* Notice Banner */}
      <div className="goal-banner">
        <span className="goal-banner-icon" aria-hidden="true">✦</span>
        <div className="goal-banner-content">
          <strong>Giao diện US-01:</strong> Đang kết nối API chuẩn <code>/api/goals</code>.
          Giao diện hỗ trợ đầy đủ tạo, sửa (kiểm soát version), xóa và ràng buộc tối đa 90 ngày.
        </div>
      </div>

      {/* Feedback Alert */}
      {successMessage && (
        <div className="goal-alert goal-alert--success" role="status">
          <span className="goal-alert-icon" aria-hidden="true">✓</span>
          <span>{successMessage}</span>
        </div>
      )}

      {/* Unauthenticated Alert */}
      {!currentUser && isUnauthenticated && (
        <div className="goal-alert goal-alert--info" role="alert">
          <div>
            <strong>Chưa xác thực:</strong> Bạn cần đăng nhập để lưu trữ mục tiêu cá nhân.
          </div>
          <button
            type="button"
            className="button button--primary button--sm"
            onClick={onNavigateAuth || (() => { window.location.hash = '#login' })}
          >
            Đăng nhập ngay →
          </button>
        </div>
      )}

      {pageError && (
        <div className="goal-alert goal-alert--error" role="alert">
          <span className="goal-alert-icon" aria-hidden="true">⚠</span>
          <span>{pageError}</span>
        </div>
      )}

      <div className="goals-layout">
        <section className="goal-form-card" aria-labelledby="goal-form-title">
          <div className="goal-card-heading">
            <h2 id="goal-form-title">{editing ? 'Chỉnh sửa mục tiêu' : 'Tạo mục tiêu mới'}</h2>
            <span className="goal-card-badge">{editing ? `Phiên bản v${editing.version}` : 'US-01 Form'}</span>
          </div>
          <form onSubmit={submitForm} noValidate className="goal-form">
            <div className="goal-field">
              <label htmlFor="goal-subject">Môn học</label>
              <input
                id="goal-subject"
                name="subject"
                value={form.subject}
                onChange={changeField}
                maxLength={100}
                placeholder="Ví dụ: Cơ sở dữ liệu"
                required
                aria-invalid={Boolean(formErrors.subject)}
              />
              {formErrors.subject && <small className="field-error">{formErrors.subject}</small>}
            </div>
            <div className="goal-field">
              <label htmlFor="goal-topics">Chủ đề cần ôn tập</label>
              <textarea
                id="goal-topics"
                name="topics"
                value={form.topics}
                onChange={changeField}
                rows={3}
                placeholder="Ví dụ: SQL, chuẩn hóa, transaction..."
                required
                aria-invalid={Boolean(formErrors.topics)}
              />
              {formErrors.topics && <small className="field-error">{formErrors.topics}</small>}
            </div>
            <div className="goal-date-grid">
              <div className="goal-field">
                <label htmlFor="goal-start">Ngày bắt đầu</label>
                <input
                  id="goal-start"
                  type="date"
                  name="startDate"
                  value={form.startDate}
                  onChange={changeField}
                  required
                  aria-invalid={Boolean(formErrors.startDate)}
                />
                <small className="field-hint">Ngày bắt đầu ôn tập.</small>
                {formErrors.startDate && <small className="field-error">{formErrors.startDate}</small>}
              </div>
              <div className="goal-field">
                <label htmlFor="goal-exam">Ngày thi</label>
                <input
                  id="goal-exam"
                  type="date"
                  name="examDate"
                  value={form.examDate}
                  min={examMin}
                  max={examMax || undefined}
                  onChange={changeField}
                  required
                  aria-invalid={Boolean(formErrors.examDate)}
                />
                <small className="field-hint">Tối đa 90 ngày kể từ ngày bắt đầu.</small>
                {formErrors.examDate && <small className="field-error">{formErrors.examDate}</small>}
              </div>
            </div>
            <div className="goal-field">
              <label htmlFor="goal-budget">Thời gian học mỗi ngày</label>
              <div className="goal-budget-input">
                <input
                  id="goal-budget"
                  type="number"
                  name="minutesPerDay"
                  value={form.minutesPerDay}
                  onChange={changeField}
                  min="15"
                  max="480"
                  step="1"
                  required
                  aria-invalid={Boolean(formErrors.minutesPerDay)}
                />
                <span>phút / ngày</span>
              </div>
              <small className="field-hint">Cho phép từ 15 đến 480 phút mỗi ngày.</small>
              {formErrors.minutesPerDay && <small className="field-error">{formErrors.minutesPerDay}</small>}
            </div>
            <div className="goal-form-actions">
              <button className="button button--primary" type="submit" disabled={saving}>
                {saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tạo mục tiêu'}
              </button>
              {editing && (
                <button className="button button--secondary" type="button" onClick={resetForm} disabled={saving}>
                  Hủy sửa
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="goal-list-card" aria-labelledby="goal-list-title">
          <div className="goal-card-heading">
            <h2 id="goal-list-title">Mục tiêu của tôi</h2>
            <span className="goal-card-badge">{goals.length} mục tiêu</span>
          </div>
          {loading ? (
            <div className="goal-empty" role="status">
              <span className="goal-empty-icon" aria-hidden="true">⏳</span>
              <p>Đang tải mục tiêu…</p>
            </div>
          ) : goals.length === 0 ? (
            <div className="goal-empty">
              <span className="goal-empty-icon" aria-hidden="true">✦</span>
              <h3>Chưa có mục tiêu nào</h3>
              <p>Tạo mục tiêu đầu tiên ở biểu mẫu bên trái để bắt đầu lập kế hoạch ôn tập.</p>
            </div>
          ) : (
            <div className="goal-list">
              {goals.map(goal => (
                <article className="goal-item" key={goal.id}>
                  <div className="goal-item-top">
                    <div className="goal-item-title-group">
                      <h3>{goal.subject}</h3>
                      <span className="goal-version">v{goal.version}</span>
                    </div>
                    <span className="goal-budget">{goal.minutesPerDay} phút/ngày</span>
                  </div>
                  <p className="goal-topics">{goal.topics}</p>
                  <p className="goal-dates">Bắt đầu {displayDate(goal.startDate)} · Thi {displayDate(goal.examDate)}</p>
                  <div className="goal-item-actions">
                    <button type="button" className="button button--secondary button--sm" onClick={() => startEditing(goal)}>
                      Chỉnh sửa
                    </button>
                    <button
                      type="button"
                      className="button button--danger button--sm"
                      onClick={() => removeGoal(goal)}
                      disabled={deletingId === goal.id}
                    >
                      {deletingId === goal.id ? 'Đang xóa…' : 'Xóa'}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
      <p className="goal-page-note">Dữ liệu chỉ hiển thị từ API của tài khoản đang đăng nhập. Máy chủ phải kiểm tra quyền sở hữu; giao diện không tự quyết định quyền truy cập.</p>
    </section>
  )
}
