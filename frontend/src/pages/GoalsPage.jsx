import { useCallback, useEffect, useState } from 'react'
import { createGoal, deleteGoal, GoalApiError, listGoals, updateGoal, parseSyllabusDocument } from '../lib/goals.js'
import { describeGoalError } from '../lib/goalErrors.js'
import { confirmAndDeleteGoal } from '../lib/goalDelete.js'
import { generatePlanDraft, getGoalDraftStatus, setGoalDraftStatus } from '../lib/ai-planner.js'
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
  return describeGoalError(error).message
}

/** Field errors from the server as one text per field (the form shows a single message under each input). */
function mapFieldErrors(error) {
  return Object.fromEntries(
    Object.entries(describeGoalError(error).fieldErrors).map(([field, messages]) => [field, messages.join(' ')]),
  )
}

export default function GoalsPage({ currentUser, onBack, onNavigateDaily, onNavigateAuth, onReviewDraft }) {
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
  const [draftStatuses, setDraftStatuses] = useState(() => {
    if (typeof window !== 'undefined' && currentUser?.email === 'bao.tonvangia@hcmut.edu.vn') {
      const cleanMarker = 'ai_study_planner_cleaned_goals_v2_bao.tonvangia'
      if (!localStorage.getItem(cleanMarker)) {
        try {
          Object.keys(localStorage).forEach(k => {
            if (k.startsWith('ai_study_planner_draft_status_') || k.includes('bao.tonvangia') || (currentUser?.id && k.includes(currentUser.id))) {
              localStorage.removeItem(k)
            }
          })
          localStorage.setItem(cleanMarker, 'true')
        } catch {
          // ignore
        }
      }
    }
    return {}
  })

  // Syllabus Parsing State (Gemini 3.8 Flash)
  const [showSyllabusBox, setShowSyllabusBox] = useState(false)
  const [isParsingSyllabus, setIsParsingSyllabus] = useState(false)
  const [syllabusText, setSyllabusText] = useState('')
  const [syllabusFileName, setSyllabusFileName] = useState('')
  const [syllabusError, setSyllabusError] = useState('')

  function getStatus(goalId) {
    return draftStatuses[goalId] ?? getGoalDraftStatus(goalId)
  }

  const updateGoalStatus = useCallback((goalId, statusObj) => {
    setGoalDraftStatus(goalId, statusObj)
    setDraftStatuses(prev => ({ ...prev, [goalId]: statusObj }))
  }, [])

  const generateAiPlan = useCallback(async (goal) => {
    if (!goal?.id) return
    updateGoalStatus(goal.id, { status: 'generating' })
    try {
      const draft = await generatePlanDraft(goal)
      updateGoalStatus(goal.id, {
        status: 'ready',
        draftId: draft.draftId,
        taskCount: draft.tasks?.length || 0,
        draft,
      })
    } catch (err) {
      updateGoalStatus(goal.id, {
        status: 'error',
        message: err.message || 'Lỗi khi gọi AI Gemini Flash',
      })
    }
  }, [updateGoalStatus])

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

  function resetForm() {
    setEditing(null)
    setForm(emptyForm)
    setFormErrors({})
    setSyllabusFileName('')
    setSyllabusText('')
  }

  function applySyllabusResult(data) {
    if (!data) return
    setForm(prev => ({
      ...prev,
      subject: data.subject || prev.subject,
      topics: data.topics || prev.topics,
      startDate: data.startDate || prev.startDate || localDateString(),
      examDate: data.examDate || prev.examDate || '',
      minutesPerDay: String(data.minutesPerDay || prev.minutesPerDay || '90'),
    }))
    setFormErrors({})
    setSuccessMessage('✓ Đã điền nhanh thông tin từ đề cương môn học bằng Gemini 3.8 Flash! Hãy xem lại và bấm Tạo mục tiêu.')
    setShowSyllabusBox(false)
    setTimeout(() => setSuccessMessage(''), 5000)
  }

  async function handleSyllabusFileUpload(event) {
    const file = event.target.files?.[0]
    if (!file) return
    setSyllabusFileName(file.name)
    setSyllabusError('')
    setIsParsingSyllabus(true)

    try {
      let result
      const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')
      if (isPdf) {
        const base64 = await new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => {
            const res = reader.result
            const b64 = typeof res === 'string' ? res.split(',')[1] : ''
            resolve(b64)
          }
          reader.onerror = reject
          reader.readAsDataURL(file)
        })
        result = await parseSyllabusDocument({
          base64,
          mimeType: 'application/pdf',
          fileName: file.name,
        })
      } else {
        const text = await file.text()
        result = await parseSyllabusDocument({
          content: text,
          fileName: file.name,
          mimeType: file.type || 'text/plain',
        })
      }
      applySyllabusResult(result)
    } catch (err) {
      setSyllabusError(err.message || 'Không thể trích xuất đề cương môn học.')
    } finally {
      setIsParsingSyllabus(false)
      event.target.value = ''
    }
  }

  async function handleParseSyllabusText() {
    if (!syllabusText.trim()) {
      setSyllabusError('Vui lòng nhập nội dung đề cương môn học.')
      return
    }
    setSyllabusError('')
    setIsParsingSyllabus(true)
    try {
      const result = await parseSyllabusDocument({
        content: syllabusText.trim(),
        fileName: 'de_cuong.txt',
        mimeType: 'text/plain',
      })
      applySyllabusResult(result)
    } catch (err) {
      setSyllabusError(err.message || 'Không thể trích xuất đề cương môn học.')
    } finally {
      setIsParsingSyllabus(false)
    }
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
    }

    try {
      if (editing) {
        await updateGoal(editing.id, payload, editing.version)
        setSuccessMessage('Đã cập nhật mục tiêu thành công!')
      } else {
        const created = await createGoal(payload)
        setSuccessMessage('Đã tạo mục tiêu ôn tập thành công! Đang dùng AI (Gemini Flash) sinh lịch học...')
        if (created?.id) {
          generateAiPlan(created)
        }
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
      await refresh()
    } catch (error) {
      setPageError(apiMessage(error))
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
        <div className="goals-heading-info">
          <p className="eyebrow">SPRINT 3 · US-06 & AI FEAT</p>
          <h1 id="goals-title">Mục tiêu ôn tập</h1>
          <p className="goals-subtitle">Tạo mục tiêu rõ ràng để giới hạn thời gian và chuẩn bị cho kỳ thi với sự hỗ trợ của Gemini 3.8 Flash.</p>
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
          <strong>Chuẩn hóa API & AI US-06:</strong> Kết nối <code>/api/v1/auth</code> và <code>/api/v1/goals</code>.
          Hỗ trợ đọc đề cương bằng model <strong>Gemini 3.8 Flash</strong> và sinh lịch học lặp lại hàng tuần.
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
            <div className="goal-card-heading-tags">
              <span className="goal-card-badge">{editing ? `Phiên bản v${editing.version}` : 'US-01 Form'}</span>
            </div>
          </div>

          {/* Quickfill Syllabus Component using Gemini 3.8 Flash */}
          <div className="syllabus-quickfill-box">
            <div className="syllabus-quickfill-topbar">
              <button
                type="button"
                className="button button--secondary button--sm syllabus-trigger-btn"
                onClick={() => setShowSyllabusBox(prev => !prev)}
                disabled={isParsingSyllabus}
              >
                <span className="syllabus-btn-icon" aria-hidden="true">📄</span>
                <span>{showSyllabusBox ? 'Đóng công cụ tải đề cương' : 'Tải file đề cương để điền nhanh'}</span>
              </button>
              <span className="syllabus-gemini-tag">Gemini 3.8 Flash</span>
            </div>

            {showSyllabusBox && (
              <div className="syllabus-quickfill-content">
                <p className="syllabus-quickfill-desc">
                  Tải lên file đề cương môn học (<strong>.pdf</strong>, <strong>.docx</strong>, <strong>.txt</strong>, <strong>.md</strong>) hoặc dán văn bản để AI tự động trích xuất tên môn, các chương mục ôn tập và đề xuất ngày thi.
                </p>

                <div className="syllabus-upload-actions">
                  <label className={`button button--primary button--sm syllabus-file-label ${isParsingSyllabus ? 'button--loading' : ''}`}>
                    <span>{isParsingSyllabus ? '⏳ Đang dùng Gemini 3.8 Flash đọc file...' : '📁 Chọn tệp đề cương (.pdf, .txt, .docx, .md)'}</span>
                    <input
                      type="file"
                      accept=".pdf,.docx,.txt,.md,.text"
                      onChange={handleSyllabusFileUpload}
                      disabled={isParsingSyllabus}
                      style={{ display: 'none' }}
                    />
                  </label>
                  {syllabusFileName && <span className="syllabus-chosen-filename">Tệp: {syllabusFileName}</span>}
                </div>

                <div className="syllabus-divider"><span>HOẶC DÁN VĂN BẢN ĐỀ CƯƠNG VÀO ĐÂY</span></div>

                <textarea
                  className="syllabus-textarea"
                  rows={3}
                  placeholder="Dán nội dung đề cương môn học, các chương mục trọng tâm vào đây..."
                  value={syllabusText}
                  onChange={e => setSyllabusText(e.target.value)}
                  disabled={isParsingSyllabus}
                />

                <div className="syllabus-text-actions">
                  <button
                    type="button"
                    className="button button--secondary button--sm"
                    onClick={handleParseSyllabusText}
                    disabled={isParsingSyllabus || !syllabusText.trim()}
                  >
                    {isParsingSyllabus ? '⏳ Đang phân tích...' : '✨ Điền nhanh bằng Gemini Flash →'}
                  </button>
                </div>

                {syllabusError && (
                  <div className="syllabus-error-message">
                    <span>⚠ {syllabusError}</span>
                  </div>
                )}
              </div>
            )}
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
                  onChange={changeField}
                  min={examMin}
                  max={examMax}
                  required
                  aria-invalid={Boolean(formErrors.examDate)}
                />
                <small className="field-hint">Tối đa 90 ngày sau ngày bắt đầu.</small>
                {formErrors.examDate && <small className="field-error">{formErrors.examDate}</small>}
              </div>
            </div>
            <div className="goal-field">
              <label htmlFor="goal-minutes">Số phút học mỗi ngày</label>
              <input
                id="goal-minutes"
                type="number"
                name="minutesPerDay"
                min={15}
                max={480}
                step={15}
                value={form.minutesPerDay}
                onChange={changeField}
                required
                aria-invalid={Boolean(formErrors.minutesPerDay)}
              />
              <small className="field-hint">Từ 15 đến 480 phút.</small>
              {formErrors.minutesPerDay && <small className="field-error">{formErrors.minutesPerDay}</small>}
            </div>
            <div className="goal-form-actions">
              <button className="button button--primary" type="submit" disabled={saving}>
                {saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tạo mục tiêu'}
              </button>
              {editing && (
                <button className="button button--secondary" type="button" onClick={resetForm} disabled={saving}>
                  Hủy chỉnh sửa
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="goal-list-card" aria-labelledby="goal-list-title">
          <div className="goal-card-heading">
            <h2 id="goal-list-title">Mục tiêu của tôi</h2>
            <button className="button button--secondary button--sm" type="button" onClick={refresh} disabled={loading}>
              Tải lại
            </button>
          </div>
          {loading && <p className="goal-list-empty">Đang tải danh sách mục tiêu…</p>}
          {!loading && goals.length === 0 && (
            <div className="goal-list-empty">
              <p>Chưa có mục tiêu nào được lưu.</p>
              <p className="empty-hint">Hãy điền form bên trái hoặc dùng nút <strong>"Tải file đề cương để điền nhanh"</strong> để bắt đầu!</p>
            </div>
          )}
          {!loading && goals.length > 0 && (
            <div className="goal-items">
              {goals.map(goal => (
                <article key={goal.id} className="goal-item">
                  <header className="goal-item-header">
                    <h3>{goal.subject}</h3>
                    <span className="goal-version">v{goal.version}</span>
                  </header>
                  <p className="goal-topics">{goal.topics}</p>
                  <dl className="goal-meta">
                    <div>
                      <dt>Bắt đầu:</dt>
                      <dd>{displayDate(goal.startDate)}</dd>
                    </div>
                    <div>
                      <dt>Ngày thi:</dt>
                      <dd>{displayDate(goal.examDate)}</dd>
                    </div>
                    <div>
                      <dt>Ngân sách:</dt>
                      <dd>{goal.minutesPerDay} phút/ngày</dd>
                    </div>
                  </dl>

                  {/* US-06 AI Generation Status Banner */}
                  {getStatus(goal.id) && (
                    <div className={`goal-ai-status-banner goal-ai-status-banner--${getStatus(goal.id)?.status}`}>
                      {getStatus(goal.id)?.status === 'generating' && (
                        <div className="goal-ai-status-content">
                          <span className="goal-ai-spinner" aria-hidden="true">⏳</span>
                          <div>
                            <strong>AI đang sinh lịch học:</strong> Đang dùng Gemini Flash phân tích chủ đề và lập lịch hàng tuần...
                          </div>
                        </div>
                      )}

                      {getStatus(goal.id)?.status === 'ready' && (
                        <div className="goal-ai-status-content">
                          <span className="goal-ai-icon" aria-hidden="true">✨</span>
                          <div className="goal-ai-text">
                            <strong>Đã sinh xong kế hoạch học tập ({getStatus(goal.id)?.taskCount || 'nhiều'} buổi học)!</strong>
                            <p>Bạn có thể mở lịch học để xem trước, kéo thả điều chỉnh các buổi học lặp lại theo tuần.</p>
                          </div>
                          <button
                            type="button"
                            className="button button--primary button--sm goal-ai-review-btn"
                            onClick={() => {
                              if (onReviewDraft && getStatus(goal.id)?.draft) {
                                onReviewDraft(getStatus(goal.id).draft)
                              } else if (onNavigateDaily) {
                                onNavigateDaily()
                              }
                            }}
                          >
                            Xem lịch & Điều chỉnh →
                          </button>
                        </div>
                      )}

                      {getStatus(goal.id)?.status === 'confirmed' && (
                        <div className="goal-ai-status-content goal-ai-status-content--confirmed">
                          <span className="goal-ai-icon" aria-hidden="true">✓</span>
                          <div>
                            <strong>Đã thêm vào lịch:</strong> Toàn bộ các buổi học đã được xác nhận và đưa vào lịch học chính thức.
                          </div>
                        </div>
                      )}

                      {getStatus(goal.id)?.status === 'error' && (
                        <div className="goal-ai-status-content goal-ai-status-content--error">
                          <span className="goal-ai-icon" aria-hidden="true">⚠</span>
                          <div>
                            <strong>Không thể sinh lịch:</strong> {getStatus(goal.id)?.message}
                          </div>
                          <button
                            type="button"
                            className="button button--secondary button--sm"
                            onClick={() => generateAiPlan(goal)}
                          >
                            Thử lại
                          </button>
                        </div>
                      )}
                    </div>
                  )}

                  <div className="goal-item-actions">
                    {!getStatus(goal.id) && (
                      <button
                        type="button"
                        className="button button--secondary button--sm button--ai"
                        onClick={() => generateAiPlan(goal)}
                        title="Tạo lịch học tự động bằng Gemini Flash"
                      >
                        ✨ Sinh lịch AI
                      </button>
                    )}
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
