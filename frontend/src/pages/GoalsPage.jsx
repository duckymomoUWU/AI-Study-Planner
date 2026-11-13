import { useCallback, useEffect, useRef, useState } from 'react'
import { deleteGoal, GoalApiError, getGoal, listGoals, updateGoal } from '../lib/goals.js'
import { describeGoalError } from '../lib/goalErrors.js'
import { createGoalWithPlan, startDateError, vietnamToday } from '../lib/goalPlanning.js'
import { studyDates, requestRevision, applySyllabusFields, planningScopeForForm, savedPlanningScope } from '../lib/planningScope.js'
import { confirmAndDeleteGoal } from '../lib/goalDelete.js'
import GoalProgress from '../components/GoalProgress.jsx'
import PlanDraftPanel from '../components/PlanDraftPanel.jsx'
import StudyBudgetPanel from '../components/StudyBudgetPanel.jsx'
import CalendarSyncPanel from '../components/CalendarSyncPanel.jsx'
import SyllabusUpload from '../components/SyllabusUpload.jsx'
import './GoalsPage.css'

const emptyForm = {
  subject: '',
  topics: '',
  startDate: '',
  examDate: '',
  minutesPerDay: '',
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

export default function GoalsPage({ onBack, onNavigateDaily, currentUser, editRequest, onEditRequestHandled }) {
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
  const [automaticPlan, setAutomaticPlan] = useState(null)
  const [selectedTopics, setSelectedTopics] = useState([])
  const [topicsConfirmed, setTopicsConfirmed] = useState(false)
  const [dailyMinutes, setDailyMinutes] = useState({})
  const [createdFromForm, setCreatedFromForm] = useState(null)
  const inputRevision = useRef(requestRevision())
  const detailRef = useRef(null)
  const editFocusRef = useRef(null)
  const [sharedBudgetFocus, setSharedBudgetFocus] = useState(0)
  useEffect(() => {
    if (!editing || !editFocusRef.current) return
    const field = document.getElementById(editFocusRef.current)
    field?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    field?.focus({ preventScroll: true })
    editFocusRef.current = null
  }, [editing])
  useEffect(() => {
    if (!editRequest || !currentUser) return
    let active = true
    getGoal(editRequest.id).then(goal => { if (active) { startEditing(goal, editRequest.section); onEditRequestHandled?.() } })
      .catch(err => { if (active) { setPageError(err.message); onEditRequestHandled?.() } })
    return () => { active = false }
  }, [editRequest, currentUser, onEditRequestHandled])
  useEffect(() => {
    const gate = inputRevision.current
    return () => { gate.next() }
  }, [])

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

  useEffect(() => {
    if (detail?.id) detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [detail?.id, automaticPlan?.status])

  function changeField(event) {
    const { name, value } = event.target
    inputRevision.current.next()
    if (saving) { setAutomaticPlan(null); setDetail(null) }
    if (name === 'topics') { setSelectedTopics([]); setTopicsConfirmed(false) }
    setForm(current => ({ ...current, [name]: value }))
    setFormErrors(current => ({ ...current, [name]: '' }))
  }

  function startEditing(goal, section) {
    editFocusRef.current = section === 'topics' ? 'goal-topics' : section === 'time' ? 'goal-budget' : null
    inputRevision.current.next()
    const saved = savedPlanningScope(goal)
    setTopicsConfirmed(true); setSelectedTopics(saved.selectedTopics); setDailyMinutes(saved.dailyMinutes || {}); setAutomaticPlan(null); setDetail(null); setCreatedFromForm(null)
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
    if (!editFocusRef.current) window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function resetForm() {
    inputRevision.current.next()
    setTopicsConfirmed(false); setSelectedTopics([]); setDailyMinutes({})
    setCreatedFromForm(null)
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
    if (saving) return
    setSaving(true)
    setPageError('')
    setSuccessMessage('')
    setFormErrors({})
    setConflicts([])

    const startError = startDateError(form.startDate, Boolean(editing))
    if (startError) {
      setFormErrors({ startDate: startError })
      setSaving(false)
      return
    }

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

    const planningScope = planningScopeForForm(form, selectedTopics, dailyMinutes)
    const payload = {
      subject: form.subject,
      topics: form.topics,
      startDate: form.startDate,
      examDate: form.examDate,
      minutesPerDay: Number(form.minutesPerDay),
      planningScope,
    }

    if (!editing && !topicsConfirmed) {
      setFormErrors({ topics: 'Hãy rà soát và xác nhận các chủ đề cần ôn trước khi sinh lịch.' })
      setSaving(false); return
    }
    const ticket = inputRevision.current.next()

    try {
      if (editing) {
        const updated = await updateGoal(editing.id, payload, editing.version)
        if (detail?.id === updated.id) setDetail(updated)
        setSuccessMessage('Đã cập nhật mục tiêu thành công!')
      } else {
        const result = await createGoalWithPlan(payload, { planningScope,
          ...(createdFromForm ? { create: value => updateGoal(createdFromForm.id, value, createdFromForm.version) } : {}),
          onCreated: goal => {
          setCreatedFromForm(goal)
          setGoals(previous => [goal, ...previous.filter(item => item.id !== goal.id)])
          if (!inputRevision.current.isCurrent(ticket)) return
          setDetail(goal)
          setAutomaticPlan({ goalId: goal.id, status: 'generating', scope: planningScope })
          setSuccessMessage('Đã tạo mục tiêu. AI đang sinh bản nháp lịch học để bạn kiểm tra…')
        } })
        if (!inputRevision.current.isCurrent(ticket)) return
        if (result.planError) {
          const reason = result.planError.status === 503 ? 'AI hiện chưa sẵn sàng.' : result.planError.message
          setAutomaticPlan({ goalId: result.goal.id, status: 'error', scope: planningScope, error: `Mục tiêu đã được lưu. Chưa sinh được lịch: ${reason} Hãy thử lại bằng nút Tạo bản nháp AI; không cần tạo lại mục tiêu.` })
          setSuccessMessage('Đã lưu mục tiêu; lịch học đang chờ sinh bản nháp.')
        } else {
          setAutomaticPlan({ goalId: result.goal.id, status: 'ready', draft: result.draft, scope: planningScope })
          setSuccessMessage('Bản nháp lịch học đã sẵn sàng ở Chi tiết mục tiêu. Hãy xem lại rồi bấm Xác nhận và lưu kế hoạch.')
        }
      }
      setTimeout(() => setSuccessMessage(''), 4000)
      if (editing) resetForm()
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

  const activePlan = automaticPlan?.goalId === detail?.id ? automaticPlan : null

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
            <button className="button button--primary" type="button" onClick={() => onNavigateDaily()}>Xem lịch học hôm nay →</button>
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

      {currentUser && <StudyBudgetPanel revision={detail?.version} focusRequest={sharedBudgetFocus} />}
      {currentUser && <CalendarSyncPanel revision={`${goals.length}:${detail?.version ?? ''}`} onOpenCalendar={() => window.location.assign('/?calendar=manage#daily')} />}
      <div className="goals-layout">
        <section className="goal-form-card" aria-labelledby="goal-form-title">
          <div className="goal-card-heading">
            <h2 id="goal-form-title">{editing ? 'Chỉnh sửa mục tiêu' : createdFromForm ? 'Điều chỉnh mục tiêu vừa tạo' : 'Tạo mục tiêu mới'}</h2>
            {editing && <span>Phiên bản v{editing.version}</span>}
          </div>
          <SyllabusUpload key={editing?.id || 'new'} disabled={saving} onApply={data => {
            inputRevision.current.next()
            setForm(previous => applySyllabusFields(previous, data))
            setSelectedTopics(data.selectedTopics); setTopicsConfirmed(true)
            setFormErrors({})
            setSuccessMessage('Đã xác nhận chủ đề. Hãy kiểm tra ngày thi và nhập quỹ phút học rồi tạo bản nháp.')
          }} />
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
              <label><input type="checkbox" disabled={saving} checked={topicsConfirmed} onChange={e => { inputRevision.current.next(); setTopicsConfirmed(e.target.checked) }} /> Tôi xác nhận các chủ đề trên là phạm vi cần ôn.</label>
            </div>
            <div className="goal-date-grid">
              <div className="goal-field">
                <label htmlFor="goal-start">Ngày bắt đầu</label>
                <input id="goal-start" type="date" name="startDate" value={form.startDate}
                  min={editing ? undefined : vietnamToday()}
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
              <label htmlFor="goal-budget">Mức mong muốn mỗi ngày cho môn này</label>
              <div className="goal-budget-input">
                <input id="goal-budget" type="number" name="minutesPerDay" value={form.minutesPerDay}
                  onChange={changeField} min="15" max="480" step="1" required
                  aria-invalid={Boolean(formErrors.minutesPerDay)} />
                <span>phút / ngày</span>
              </div>
              <small className="field-hint">15–480 phút/ngày để AI ưu tiên phân bổ. Đây không phải giới hạn cứng; tổng mọi môn phải nằm trong quỹ chung. AI đề xuất vượt mức này cần bạn đồng ý.</small>
              <FieldError formErrors={formErrors} name="minutesPerDay" />
              <small>Chỉ nhập tổng phút theo ngày; đây không phải một khung giờ liên tục. Các ước lượng của AI có thể chỉnh.</small>
            </div>
            {studyDates(form.startDate, form.examDate).length > 0 && <details>
              <summary>Điều chỉnh phút từng ngày (0 = không học)</summary>
              {studyDates(form.startDate, form.examDate).map(day => <label key={day} className="goal-field">{displayDate(day)}
                <input type="number" min={0} max={480} step={1} value={dailyMinutes[day] ?? form.minutesPerDay} onChange={e => {
                  inputRevision.current.next(); if (saving) { setAutomaticPlan(null); setDetail(null) }
                  setDailyMinutes(previous => ({ ...previous, [day]: e.target.value === '' ? '' : Number(e.target.value) }))
                }} />
              </label>)}
            </details>}
            <div className="goal-form-actions">
              <button className="button button--primary" type="submit" disabled={saving}>
                {saving ? automaticPlan?.status === 'generating' ? 'Đang sinh lịch học…' : 'Đang lưu…' : editing ? 'Lưu thay đổi' : createdFromForm ? 'Cập nhật mục tiêu này & sinh lại bản nháp' : 'Tạo mục tiêu & sinh lịch AI'}
              </button>
              {!editing && createdFromForm && <button className="button button--secondary" type="button" onClick={resetForm} disabled={saving}>Tạo mục tiêu khác</button>}
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
            <section ref={detailRef} className="goal-detail" aria-label="Chi tiết mục tiêu">
              <div className="goal-item-top">
                <h3>{detail.subject}</h3>
                <button type="button" onClick={() => setDetail(null)}>Đóng</button>
              </div>
              <dl>
                <dt>Chủ đề</dt><dd>{detail.topics}</dd>
                <dt>Ngày bắt đầu</dt><dd>{displayDate(detail.startDate)}</dd>
                <dt>Ngày thi</dt><dd>{displayDate(detail.examDate)}</dd>
                <dt>Mức mong muốn mỗi ngày</dt><dd>{detail.minutesPerDay} phút</dd>
                <dt>Múi giờ</dt><dd>{detail.timezone}</dd>
                <dt>Phiên bản</dt><dd>v{detail.version}</dd>
              </dl>
              <GoalProgress goalId={detail.id} version={detail.version} />
              {detail.planningScope?.selectedTopics && <details>
                <summary>Cấu hình kế hoạch đã lưu</summary>
                <p>Chủ đề, ưu tiên và mức mong muốn được giữ khi mở lại. Các ngày không chỉnh riêng ưu tiên {detail.minutesPerDay} phút; tổng thời gian bị giới hạn bởi quỹ chung.</p>
                <ul>{detail.planningScope.selectedTopics.map(topic => <li key={topic.id}>{topic.title} · Ưu tiên: {({ HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' })[topic.priority || 'MEDIUM']}</li>)}</ul>
                <ul>{Object.entries(detail.planningScope.dailyMinutes || {}).map(([day, minutes]) => <li key={day}>{displayDate(day)}: {minutes} phút{minutes === 0 ? ' (không học)' : ''}</li>)}</ul>
              </details>}
              <PlanDraftPanel key={`${detail.id}:${detail.version}:${activePlan?.status || 'manual'}:${activePlan?.draft?.id || ''}`} goal={detail} planningScope={activePlan?.scope}
                initialDraft={activePlan?.draft} initialError={activePlan?.error} generating={activePlan?.status === 'generating'}
                onAdjust={async section => { try { startEditing(await getGoal(detail.id), section) } catch (err) { setPageError(err.message) } }}
                onAdjustSharedBudget={() => setSharedBudgetFocus(value => value + 1)}
                onViewSchedule={() => onNavigateDaily?.()}
                onSaved={async (saved, firstDate) => {
                setAutomaticPlan(null)
                resetForm()
                setSuccessMessage(`Đã lưu ${saved.createdCount} công việc vào lịch học.${saved.remainingCount ? ` Còn ${saved.remainingCount} việc chưa xếp; mở mục tiêu → Việc chưa xếp đã lưu để xử lý tiếp.` : ''}`)
                await refresh()
                try { setDetail(await getGoal(detail.id)) } catch (err) { setPageError(err.message) }
                if (onNavigateDaily) onNavigateDaily(firstDate)
              }} />
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
                    <span className="goal-budget">Mong muốn {goal.minutesPerDay} phút/ngày</span>
                  </div>
                  <p className="goal-topics">{goal.topics}</p>
                  <p className="goal-dates">Bắt đầu {displayDate(goal.startDate)} · Thi {displayDate(goal.examDate)}</p>
                  <GoalProgress goalId={goal.id} version={goal.version} />
                  <div className="goal-item-actions">
                    <button type="button" onClick={() => showDetail(goal)} disabled={detailLoading || saving}>Chi tiết</button>
                    <button type="button" onClick={() => showDetail(goal)} disabled={detailLoading || saving}>Bản nháp AI & Điều chỉnh</button>
                    <button type="button" onClick={() => startEditing(goal)} disabled={saving}>Chỉnh sửa</button>
                    <button type="button" className="goal-delete" onClick={() => removeGoal(goal)}
                      disabled={deletingId !== null || saving}>
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
