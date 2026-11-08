import { useState } from 'react'
import { confirmPlanDraft, generatePlanDraft, getLatestPlanDraft } from '../lib/ai-planner.js'

export default function PlanDraftPanel({ goal, onSaved }) {
  const [draft, setDraft] = useState(null)
  const [items, setItems] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [attempt, setAttempt] = useState(null)

  async function load(generate) {
    setBusy(true); setError(''); setMessage('')
    try {
      const value = await (generate ? generatePlanDraft(goal.id) : getLatestPlanDraft(goal.id))
      setDraft(value); setItems(value.proposal.tasks); setAttempt(null)
      setMessage('Bản nháp chưa được lưu vào lịch học. Bạn có thể sửa trước khi xác nhận.')
    } catch (err) {
      setError(err.status === 503 ? 'AI chưa sẵn sàng. Bạn vẫn có thể tạo công việc thủ công trong Lịch học.'
        : err.status === 404 ? 'Chưa có bản nháp cho mục tiêu này.' : err.message)
    } finally { setBusy(false) }
  }

  function edit(index, field, value) {
    setItems(previous => previous.map((task, i) => i === index ? { ...task, [field]: value } : task))
    setAttempt(null)
  }

  async function confirm(event) {
    event.preventDefault()
    setBusy(true); setError(''); setMessage('')
    const savedAttempt = attempt || { key: crypto.randomUUID(), tasks: items.map(task => ({ ...task })) }
    setAttempt(savedAttempt)
    try {
      const result = await confirmPlanDraft(goal.id, draft.id, savedAttempt.tasks, draft.goalVersion, savedAttempt.key)
      setMessage(`Đã lưu ${result.createdCount} công việc. Bạn có thể xem trong Lịch học.`)
      setDraft(null); setAttempt(null)
      onSaved()
    } catch (err) {
      setError(`${err.message}${err.context?.excessMinutes ? ` Vượt ${err.context.excessMinutes} phút.` : ''}${err.context?.shortfallMinutes ? ` Thiếu ${err.context.shortfallMinutes} phút.` : ''}`)
    } finally { setBusy(false) }
  }

  return <section className="goal-detail" aria-label="Bản nháp kế hoạch AI">
    <h3>Kế hoạch ôn tập đề xuất</h3>
    <p>Ngân sách {goal.minutesPerDay} phút/ngày; không xếp việc vào ngày thi. Chỉ xác nhận mới thêm việc vào lịch.</p>
    <div className="goal-item-actions">
      <button type="button" disabled={busy} onClick={() => load(true)}>{busy ? 'Đang xử lý…' : 'Tạo bản nháp AI'}</button>
      <button type="button" disabled={busy} onClick={() => load(false)}>Mở bản nháp gần nhất</button>
    </div>
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    {draft && <form onSubmit={confirm}>
      <p>Bản nháp theo phiên bản v{draft.goalVersion}. Nếu mục tiêu hoặc công việc đã thay đổi, hãy tạo lại bản nháp.</p>
      {items.map((task, index) => <fieldset key={index} disabled={busy} className="goal-field">
        <legend>Công việc {index + 1}</legend>
        <label>Tiêu đề<input aria-label={`Tiêu đề công việc ${index + 1}`} value={task.title} required maxLength={200} onChange={e => edit(index, 'title', e.target.value)} /></label>
        <label>Ngày học<input type="date" aria-label={`Ngày học công việc ${index + 1}`} required min={goal.startDate} value={task.scheduledDate} onChange={e => edit(index, 'scheduledDate', e.target.value)} /></label>
        <label>Số phút<input type="number" aria-label={`Số phút công việc ${index + 1}`} required min={1} max={480} step={1} value={task.estimatedMinutes} onChange={e => edit(index, 'estimatedMinutes', e.target.value)} /></label>
        <button type="button" onClick={() => { setItems(previous => previous.filter((_, i) => i !== index)); setAttempt(null) }}>Bỏ công việc này</button>
      </fieldset>)}
      <div className="goal-item-actions">
        <button type="submit" disabled={busy || items.length === 0}>Xác nhận và lưu kế hoạch</button>
        <button type="button" disabled={busy} onClick={() => { setDraft(null); setMessage('Đã đóng bản nháp; lịch học không thay đổi.') }}>Đóng bản nháp</button>
      </div>
    </form>}
  </section>
}
