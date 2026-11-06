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

function displayDate(value) {
  if (!value) return 'Chưa có ngày'
  return new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T12:00:00`))
}

function apiMessage(error) {
  if (error instanceof GoalApiError && error.status === 401) {
    return 'Bạn cần đăng nhập để quản lý mục tiêu. Luồng đăng nhập ASP-12 chưa được kết nối.'
  }
  if (error instanceof GoalApiError && error.status === 404) {
    return 'API mục tiêu chưa khả dụng hoặc mục tiêu không còn tồn tại. Backend Goal CRUD vẫn đang được hoàn thiện.'
  }
  if (error instanceof GoalApiError && error.status === 409) {
    return 'Mục tiêu vừa được cập nhật ở nơi khác. Hãy tải lại danh sách trước khi sửa tiếp.'
  }
  return error?.message || 'Đã xảy ra lỗi. Vui lòng thử lại.'
}

function mapFieldErrors(error) {
  return Object.fromEntries((error?.fieldErrors || []).map(item => [item.field, item.message]))
}

export default function GoalsPage({ onBack }) {
  const [goals, setGoals] = useState([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState(null)
  const [pageError, setPageError] = useState('')
  const [formErrors, setFormErrors] = useState({})
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(emptyForm)

  const refresh = useCallback(async () => {
    setLoading(true)
    setPageError('')
    try {
      const items = await listGoals()
      setGoals(items)
    } catch (error) {
      setPageError(apiMessage(error))
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
        if (!controller.signal.aborted && error.name !== 'AbortError') setPageError(apiMessage(error))
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
    setFormErrors({})
    const payload = {
      subject: form.subject,
      topics: form.topics,
      startDate: form.startDate,
      examDate: form.examDate,
      minutesPerDay: Number(form.minutesPerDay),
      ...(editing ? { version: editing.version } : {}),
    }
    try {
      if (editing) await updateGoal(editing.id, payload)
      else await createGoal(payload)
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
    try {
      await deleteGoal(goal.id)
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

  return (
    <section className="goals-page" aria-labelledby="goals-title">
      <div className="goals-heading">
        <div>
          <p className="eyebrow">QUẢN LÝ HỌC TẬP</p>
          <h1 id="goals-title">Mục tiêu ôn tập</h1>
          <p>Tạo mục tiêu rõ ràng để giới hạn thời gian và chuẩn bị cho kỳ thi.</p>
        </div>
        <button className="button button--secondary" type="button" onClick={onBack}>← Tổng quan</button>
      </div>

      {pageError && <div className="goal-alert" role="alert">{pageError}</div>}

      <div className="goals-layout">
        <section className="goal-form-card" aria-labelledby="goal-form-title">
          <div className="goal-card-heading">
            <h2 id="goal-form-title">{editing ? 'Chỉnh sửa mục tiêu' : 'Tạo mục tiêu mới'}</h2>
            <span>{editing ? `Phiên bản ${editing.version}` : 'Bước 1 / 3'}</span>
          </div>
          <form onSubmit={submitForm} noValidate>
            <div className="goal-field">
              <label htmlFor="goal-subject">Môn học</label>
              <input id="goal-subject" name="subject" value={form.subject} onChange={changeField}
                maxLength={100} placeholder="Ví dụ: Cơ sở dữ liệu" required aria-invalid={Boolean(formErrors.subject)} />
              {formErrors.subject && <small className="field-error">{formErrors.subject}</small>}
            </div>
            <div className="goal-field">
              <label htmlFor="goal-topics">Chủ đề cần ôn tập</label>
              <textarea id="goal-topics" name="topics" value={form.topics} onChange={changeField}
                rows={3} placeholder="Ví dụ: SQL, chuẩn hóa, transaction..." required
                aria-invalid={Boolean(formErrors.topics)} />
              {formErrors.topics && <small className="field-error">{formErrors.topics}</small>}
            </div>
            <div className="goal-date-grid">
              <div className="goal-field">
                <label htmlFor="goal-start">Ngày bắt đầu</label>
                <input id="goal-start" type="date" name="startDate" value={form.startDate}
                  onChange={changeField} required aria-invalid={Boolean(formErrors.startDate)} />
                {formErrors.startDate && <small className="field-error">{formErrors.startDate}</small>}
              </div>
              <div className="goal-field">
                <label htmlFor="goal-exam">Ngày thi</label>
                <input id="goal-exam" type="date" name="examDate" value={form.examDate}
                  min={examMin} onChange={changeField} required aria-invalid={Boolean(formErrors.examDate)} />
                {formErrors.examDate && <small className="field-error">{formErrors.examDate}</small>}
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
              {formErrors.minutesPerDay && <small className="field-error">{formErrors.minutesPerDay}</small>}
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
                  <div className="goal-item-actions">
                    <button type="button" onClick={() => startEditing(goal)}>Chỉnh sửa</button>
                    <button type="button" className="goal-delete" onClick={() => removeGoal(goal)}
                      disabled={deletingId === goal.id}>
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
