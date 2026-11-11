import { useEffect, useRef, useState } from 'react'
import { requestRevision, savedPlanningScope } from '../lib/planningScope.js'
import { firstPlannedDate } from '../lib/goalPlanning.js'
import { confirmPlanDraft, generatePlanDraft, getLatestPlanDraft, getPendingPlanDrafts, reschedulePlanDraft, draftReview, draftState, sortDraftTasksByPriority } from '../lib/ai-planner.js'
import './PlanDraftPanel.css'

export default function PlanDraftPanel({ goal, onSaved, onAdjust, onAdjustSharedBudget, onViewSchedule, initialDraft = null, initialError = '', generating = false, planningScope }) {
  const [draft, setDraft] = useState(initialDraft)
  const [items, setItems] = useState(() => initialDraft ? allItems(initialDraft) : [])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(initialError)
  const [message, setMessage] = useState(initialDraft ? 'Bản nháp đã sinh tự động. Hãy kiểm tra và xác nhận để lưu vào lịch học.' : '')
  const [attempt, setAttempt] = useState(null)
  const [backlogs, setBacklogs] = useState([])
  const revision = useRef(requestRevision())
  useEffect(() => {
    const gate = revision.current
    return () => { gate.next() }
  }, [])
  useEffect(() => {
    let active = true
    getPendingPlanDrafts(goal.id).then(values => { if (active) setBacklogs(values) })
      .catch(err => { if (active) setError(`Chưa tải được việc chưa xếp: ${err.message}`) })
    return () => { active = false }
  }, [goal.id, goal.version])
  function allItems(value) { return sortDraftTasksByPriority([...value.proposal.tasks, ...(value.proposal.unscheduled || [])]) }

  const pending = busy || generating
  const review = draftReview(items, draft?.proposal.summary?.availableMinutes || 0)
  const state = draftState(draft)

  async function openPending() {
    setBusy(true); setError('')
    try {
      const values = await getPendingPlanDrafts(goal.id)
      setBacklogs(values)
      setMessage(values.length ? 'Đây là phần còn lại khi bạn lưu một phần kế hoạch. Các việc này chưa có trong lịch học.' : 'Không có việc nào đang chờ đưa vào lịch.')
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function reschedule() {
    setBusy(true); setError('')
    try {
      const value = await reschedulePlanDraft(goal.id, draft.id)
      setDraft(value); setItems(allItems(value)); setAttempt(null)
      setMessage('Đã xếp lại theo cấu hình đã lưu, giữ nguyên ước lượng phút. Kiểm tra trước khi xác nhận.')
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function load(generate) {
    const ticket = revision.current.next()
    setBusy(true); setError(''); setMessage('')
    try {
      const scope = planningScope || savedPlanningScope(goal)
      const value = await (generate ? generatePlanDraft(goal.id, { scope }) : getLatestPlanDraft(goal.id))
      if (!revision.current.isCurrent(ticket)) return
      setDraft(value); setItems(allItems(value)); setAttempt(null)
      setMessage(draftState(value).message)
    } catch (err) {
      if (!revision.current.isCurrent(ticket)) return
      if (err.code === 'PLAN_ALREADY_SAVED') { setError(''); setMessage(err.message); return }
      setError(err.status === 503 ? 'AI chưa sẵn sàng. Bạn vẫn có thể tạo công việc thủ công trong Lịch học.'
        : err.status === 404 ? 'Chưa có bản nháp cho mục tiêu này.' : err.message)
    } finally { if (revision.current.isCurrent(ticket)) setBusy(false) }
  }

  function edit(index, field, value) {
    setItems(previous => previous.map((task, i) => i === index ? { ...task, [field]: value } : task))
    setAttempt(null)
  }

  async function confirm(event) {
    event.preventDefault()
    if (pending || state.readOnly) return
    const ticket = revision.current.next()
    setBusy(true); setError(''); setMessage('')
    const partial = review.remaining.length > 0
    const savedAttempt = attempt || { key: crypto.randomUUID(), tasks: review.scheduled.map(task => ({ ...task })), deferredTasks: review.remaining.map(task => ({ ...task })), partial }
    setAttempt(savedAttempt)
    try {
      const result = await confirmPlanDraft(goal.id, draft.id, savedAttempt.tasks, draft.goalVersion, savedAttempt.key, { allowPartial: savedAttempt.partial, deferredTasks: savedAttempt.deferredTasks })
      if (!revision.current.isCurrent(ticket)) return
      setMessage(`Đã lưu ${result.createdCount} công việc. Bạn có thể xem trong Lịch học.`)
      setDraft(null); setAttempt(null)
      onSaved(result, firstPlannedDate(savedAttempt.tasks))
    } catch (err) {
      if (!revision.current.isCurrent(ticket)) return
      setError(`${err.message}${err.context?.excessMinutes ? ` Vượt ${err.context.excessMinutes} phút.` : ''}${err.context?.shortfallMinutes ? ` Thiếu ${err.context.shortfallMinutes} phút.` : ''}`)
    } finally { if (revision.current.isCurrent(ticket)) setBusy(false) }
  }

  return <section className="goal-detail" aria-label="Bản nháp kế hoạch AI">
    <h3>Kế hoạch ôn tập đề xuất</h3>
    <p>Tối đa {goal.minutesPerDay} phút/ngày, có thể ít hơn ở ngày bạn đã điều chỉnh; không xếp vào ngày thi. Đây là tổng phút theo ngày, chưa xác định giờ bắt đầu hay một khoảng liên tục. Chỉ xác nhận mới thêm việc vào lịch.</p>
    <div className="goal-item-actions">
      <button type="button" disabled={pending} onClick={() => load(true)}>{pending ? 'Đang sinh lịch…' : draft ? 'Sinh lại bản nháp AI' : 'Tạo bản nháp AI'}</button>
      <button type="button" disabled={pending} onClick={() => load(false)}>Xem đề xuất gần nhất</button>
      {onViewSchedule && <button type="button" disabled={pending} onClick={onViewSchedule}>Xem lịch đã lưu</button>}
    </div>
    {backlogs.length > 0 && <p role="status">Kế hoạch chưa bao phủ hết nội dung: còn việc đang chờ xếp. Tiến độ phía trên chỉ tính các việc đã đưa vào lịch học.</p>}
    {backlogs.length > 0 && <details><summary>Phần còn lại chưa đưa vào lịch ({backlogs.reduce((sum, value) => sum + value.proposal.tasks.length + value.proposal.unscheduled.length, 0)} việc)</summary>
      <p>Phần này chỉ có khi bạn đã chọn lưu một phần kế hoạch. Các việc còn lại được giữ ở đây để xếp tiếp, chưa phải buổi học trong lịch.</p>
      <button type="button" disabled={pending} onClick={openPending}>Tải lại phần còn lại</button>
      {backlogs.map(value => <button key={value.id} type="button" disabled={pending} onClick={() => { setDraft(value); setItems(allItems(value)); setAttempt(null); setError(''); setMessage(draftState(value).message) }}>Mở phần còn lại · {value.proposal.tasks.length + value.proposal.unscheduled.length} việc</button>)}
    </details>}
    {generating && <p role="status">Đã lưu mục tiêu. AI đang tạo lịch học đề xuất, vui lòng đợi…</p>}
    {error && <p role="alert">{error}</p>}
    {message && <p role="status">{message}</p>}
    {draft && state.readOnly && <div className="goal-detail">
      <p role="status">{state.message}</p>
      {state.canReschedule && <button type="button" disabled={pending} onClick={reschedule}>Xếp lại theo cấu hình hiện tại</button>}
      <ul>{items.map((task, index) => <li key={task.id || index}>{task.title} · {task.estimatedMinutes} phút · {task.scheduledDate || 'Chưa có ngày học'}</li>)}</ul>
      <button type="button" onClick={() => { setDraft(null); setMessage('') }}>Đóng bản xem lại</button>
    </div>}
    {draft && !state.readOnly && <form onSubmit={confirm}>
      <p>Bản nháp theo phiên bản v{draft.goalVersion}. Nếu mục tiêu hoặc công việc đã thay đổi, hãy tạo lại bản nháp.</p>
      {draft.proposal.summary?.alreadyScheduledCount > 0 && <p role="status">Đã bỏ qua {draft.proposal.summary.alreadyScheduledCount} công việc trùng với lịch hiện có; chỉ đề xuất việc mới bên dưới.</p>}
      {draft.proposal.scope && <>
        {review.remaining.length > 0 && <section className="plan-shortage" aria-label="Điều chỉnh kế hoạch chưa đủ thời gian">
          <h4>{review.shortfallMinutes > 0 ? 'Thời gian hiện tại chưa đủ để ôn hết nội dung' : 'Còn công việc chưa có ngày học'}</h4>
          <p role="status">Đã xếp <strong>{review.scheduled.length}/{items.length} việc</strong>. Còn <strong>{review.remaining.length} việc</strong> chưa có ngày học.</p>
          {review.shortfallMinutes > 0 ? <p>Cần thêm khoảng <strong>{review.shortfallMinutes} phút</strong> theo ước lượng hiện tại.</p>
            : <p>Tổng quỹ phút có thể đủ, nhưng cần bố trí lại từng ngày để mỗi việc có đủ thời gian. Bạn cũng có thể chọn ngày học thủ công bên dưới.</p>}
          {draft.proposal.scope.sharedBudgetLimitedDays > 0 ? <p className="plan-shortage-cause">Một số ngày đang bị giới hạn bởi <strong>quỹ thời gian chung của tất cả môn</strong>. Chỉ tăng phút riêng của mục tiêu có thể chưa đủ.</p>
            : <p className="plan-shortage-cause">Quỹ riêng của mục tiêu và quỹ chung đều được kiểm tra. Nếu quỹ chung thấp, chỉ tăng phút riêng có thể chưa đủ. {onAdjustSharedBudget && <button type="button" disabled={pending} onClick={onAdjustSharedBudget}>Xem quỹ chung</button>}</p>}
          <div className="plan-shortage-actions">
            <div><button className="plan-shortage-primary" type="button" disabled={pending} onClick={() => draft.proposal.scope.sharedBudgetLimitedDays > 0 && onAdjustSharedBudget ? onAdjustSharedBudget() : onAdjust?.('time')}>Điều chỉnh thời gian</button>
              <small>{draft.proposal.scope.sharedBudgetLimitedDays > 0 ? 'Mở quỹ chung để xem thời gian còn lại của tất cả môn.' : 'Chỉnh số phút mỗi ngày dành cho mục tiêu này.'}</small></div>
            <div><button type="button" disabled={pending} onClick={() => onAdjust?.('topics')}>Chọn lại chủ đề</button><small>Giảm phạm vi ôn rồi tạo đề xuất AI mới.</small></div>
            <div><button type="submit" disabled={pending || review.scheduled.length === 0}>Lưu phần đã xếp</button><small>{review.remaining.length} việc còn lại được giữ để xếp tiếp; kế hoạch chưa bao phủ hết nội dung.</small></div>
          </div>
          <p className="plan-shortage-hint">Chỉnh quỹ phút xong, mở lại bản nháp và chọn “Xếp lại theo quỹ phút đã lưu”. Lịch đã lưu được giữ nguyên.</p>
        </section>}
        {review.remaining.length === 0 && <p>Đã xếp đủ {items.length} việc · {review.requiredMinutes} phút dự kiến · Quỹ còn lại khi tạo bản nháp: {draft.proposal.summary.availableMinutes} phút.</p>}
        <details><summary>Chủ đề và cách xếp lịch</summary><p>{draft.proposal.scope.assumption}</p>
          <p>Chủ đề đã chọn: {draft.proposal.scope.selectedTopics.map(topic => topic.title).join(', ')}</p>
        </details>
        <p>Xếp lại dùng công việc và ước lượng của bản nháp đã lưu; các chỉnh sửa chưa xác nhận trên biểu mẫu này chưa được áp dụng.</p>
        <button type="button" disabled={pending} onClick={reschedule}>Xếp lại theo quỹ phút đã lưu (giữ ước lượng)</button>
      </>}
      {items.map((task, index) => <fieldset key={index} disabled={pending} className="goal-field">
        <legend>Công việc {index + 1}{task.priority && ` · Ưu tiên: ${{ HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' }[task.priority] || task.priority}`}</legend>
        {task.topicId && <p>Chủ đề: {draft.proposal.scope.selectedTopics.find(topic => topic.id === task.topicId)?.title}</p>}
        {!task.scheduledDate && <p role="status">Chưa xếp được: {task.reason || 'Hãy chọn một ngày còn đủ phút trước ngày thi.'}</p>}
        <label>Tiêu đề<input aria-label={`Tiêu đề công việc ${index + 1}`} value={task.title} required maxLength={200} onChange={e => edit(index, 'title', e.target.value)} /></label>
        <label>Ngày học<input type="date" aria-label={`Ngày học công việc ${index + 1}`} min={goal.startDate} value={task.scheduledDate || ''} onChange={e => edit(index, 'scheduledDate', e.target.value)} /></label>
        <label>Số phút<input type="number" aria-label={`Số phút công việc ${index + 1}`} required min={1} max={480} step={1} value={task.estimatedMinutes} onChange={e => edit(index, 'estimatedMinutes', e.target.value)} /></label>
        {!draft.proposal.scope && <button type="button" onClick={() => { setItems(previous => previous.filter((_, i) => i !== index)); setAttempt(null) }}>Bỏ công việc này</button>}
      </fieldset>)}
      <div className="goal-item-actions">
        <button type="submit" disabled={pending || review.scheduled.length === 0}>{review.remaining.length ? 'Lưu phần đã xếp' : 'Xác nhận và lưu kế hoạch'}</button>
        <button type="button" disabled={pending} onClick={() => { setDraft(null); setMessage('Đã đóng bản nháp; lịch học không thay đổi.') }}>Đóng bản nháp</button>
      </div>
      {review.remaining.length > 0 && <p className="plan-shortage-hint">Lưu {review.scheduled.length} việc vào lịch. {review.remaining.length} việc còn lại được giữ để xếp tiếp.</p>}
    </form>}
  </section>
}
