import { useEffect, useRef, useState } from 'react'
import { requestRevision, savedPlanningScope } from '../lib/planningScope.js'
import { firstPlannedDate } from '../lib/goalPlanning.js'
import { confirmPlanDraft, generatePlanDraft, getLatestPlanDraft, getPendingPlanDrafts, reschedulePlanDraft, draftReview, draftState, sortDraftTasksByPriority, preferenceExcessDays } from '../lib/ai-planner.js'
import './PlanDraftPanel.css'

export default function PlanDraftPanel({ goal, onSaved, onAdjust, onAdjustSharedBudget, onViewSchedule, initialDraft = null, initialError = '', generating = false, planningScope }) {
  const [draft, setDraft] = useState(initialDraft)
  const [items, setItems] = useState(() => initialDraft ? allItems(initialDraft) : [])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(initialError)
  const [message, setMessage] = useState(initialDraft ? 'Bản nháp đã sinh tự động. Hãy kiểm tra và xác nhận để lưu vào lịch học.' : '')
  const [attempt, setAttempt] = useState(null)
  const [allowPreferenceExceedance, setAllowPreferenceExceedance] = useState(false)
  const [serverExcess, setServerExcess] = useState([])
  const [backlogs, setBacklogs] = useState([])
  const [extras, setExtras] = useState([])
  const [dirty, setDirty] = useState(false)
  const [budgetNotice, setBudgetNotice] = useState(false)
  const pendingFocus = useRef('')
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
  const heldBackCount = items.filter(task => task.heldBack).length
  const serverRemaining = review.remaining.length - heldBackCount
  const state = draftState(draft)
  const excessDays = serverExcess.length ? serverExcess : preferenceExcessDays([...items, ...extras], draft, goal)
  const needsPreferenceConsent = excessDays.length > 0 && !allowPreferenceExceedance

  function resetDecision() { setAllowPreferenceExceedance(false); setServerExcess([]); setAttempt(null) }
  function adopt(value) { setDraft(value); setItems(allItems(value)); setExtras([]); setDirty(false); setBudgetNotice(false); resetDecision() }

  async function openPending() {
    setBusy(true); setError('')
    try {
      const values = await getPendingPlanDrafts(goal.id)
      setBacklogs(values)
      setMessage(values.length ? 'Đây là phần còn lại khi bạn lưu một phần kế hoạch. Các việc này chưa có trong lịch học.' : 'Không có việc nào đang chờ đưa vào lịch.')
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function reschedule(respectPreferences, notice) {
    setBusy(true); setError('')
    try {
      const value = await reschedulePlanDraft(goal.id, draft.id, { respectPreferences: typeof respectPreferences === 'boolean' ? respectPreferences : undefined })
      adopt(value)
      setMessage(notice || 'Đã xếp lại theo cấu hình đã lưu, giữ nguyên ước lượng phút. Kiểm tra trước khi xác nhận.')
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }

  async function load(generate) {
    const ticket = revision.current.next()
    setBusy(true); setError(''); setMessage('')
    try {
      const scope = planningScope || savedPlanningScope(goal)
      const value = await (generate ? generatePlanDraft(goal.id, { scope }) : getLatestPlanDraft(goal.id))
      if (!revision.current.isCurrent(ticket)) return
      adopt(value)
      setMessage(draftState(value).message)
    } catch (err) {
      if (!revision.current.isCurrent(ticket)) return
      if (err.code === 'PLAN_ALREADY_SAVED') { setError(''); setMessage(err.message); return }
      setError(err.status === 503 ? 'AI chưa sẵn sàng. Bạn vẫn có thể tạo công việc thủ công trong Lịch học.'
        : err.status === 404 ? 'Chưa có bản nháp cho mục tiêu này.' : err.message)
    } finally { if (revision.current.isCurrent(ticket)) setBusy(false) }
  }

  function edit(index, field, value) {
    setItems(previous => previous.map((task, i) => i === index ? { ...task, [field]: value, ...(field === 'scheduledDate' && value ? { heldBack: false } : {}) } : task))
    setDirty(true); resetDecision()
  }
  // Leaving a scoped draft's work out of this save is never a delete: the server keeps it in the "remaining" draft.
  function holdBack(index) {
    pendingFocus.current = `put-${index}`
    setItems(previous => previous.map((task, i) => i === index ? { ...task, previousDate: task.scheduledDate, scheduledDate: '', heldBack: true } : task))
    setDirty(true); resetDecision()
  }
  function putBack(index) {
    pendingFocus.current = `hold-${index}`
    setItems(previous => previous.map((task, i) => i === index ? { ...task, scheduledDate: task.previousDate || '', heldBack: false } : task))
    setDirty(true); resetDecision()
  }
  function addExtra() {
    pendingFocus.current = `extra-${extras.length + 1}`
    const topicId = draft.proposal.scope.selectedTopics[0]?.id || ''
    setExtras(previous => [...previous, { key: crypto.randomUUID(), topicId, title: '', scheduledDate: '', estimatedMinutes: 30 }])
    setDirty(true); resetDecision()
  }
  function editExtra(key, field, value) { setExtras(previous => previous.map(extra => extra.key === key ? { ...extra, [field]: value } : extra)); resetDecision() }
  function removeExtra(key) { pendingFocus.current = 'add-extra'; setExtras(previous => previous.filter(extra => extra.key !== key)); resetDecision() }

  // The shared budget lives outside this panel. When it changes, the numbers of an open draft describe the old budget:
  // re-plan it at once (no AI, nothing is saved) unless the user has already edited it, in which case say so.
  const budgetHandler = useRef(null)
  budgetHandler.current = () => {
    if (!draft || draftState(draft).readOnly || !draft.proposal.scope) return
    if (dirty || pending) { setBudgetNotice(true); return }
    reschedule(undefined, 'Quỹ thời gian chung vừa thay đổi nên bản nháp đã được xếp lại theo quỹ mới. Chưa có gì được lưu vào lịch.')
  }
  // The button that was pressed is replaced by another one; keep keyboard focus on the new control.
  useEffect(() => {
    if (!pendingFocus.current) return
    document.querySelector(`[data-focus-id="${pendingFocus.current}"]`)?.focus()
    pendingFocus.current = ''
  }, [items, extras])
  useEffect(() => {
    const listener = () => budgetHandler.current()
    window.addEventListener('study-budget-saved', listener)
    return () => window.removeEventListener('study-budget-saved', listener)
  }, [])

  async function confirm(event) {
    event.preventDefault()
    if (pending || state.readOnly || needsPreferenceConsent) return
    const ticket = revision.current.next()
    setBusy(true); setError(''); setMessage('')
    const partial = review.remaining.length > 0
    const savedAttempt = attempt || { key: crypto.randomUUID(), tasks: review.scheduled.map(task => ({ ...task })), deferredTasks: review.remaining.map(task => ({ ...task })), addedTasks: extras.map(task => ({ ...task })), partial, allowPreferenceExceedance }
    setAttempt(savedAttempt)
    try {
      const result = await confirmPlanDraft(goal.id, draft.id, savedAttempt.tasks, draft.goalVersion, savedAttempt.key, { allowPartial: savedAttempt.partial, deferredTasks: savedAttempt.deferredTasks, addedTasks: savedAttempt.addedTasks, allowPreferenceExceedance: savedAttempt.allowPreferenceExceedance })
      if (!revision.current.isCurrent(ticket)) return
      setMessage(`Đã lưu ${result.createdCount} công việc. Bạn có thể xem trong Lịch học.`)
      setDraft(null); setAttempt(null)
      onSaved(result, firstPlannedDate([...savedAttempt.tasks, ...savedAttempt.addedTasks]))
    } catch (err) {
      if (!revision.current.isCurrent(ticket)) return
      if (err.code === 'PREFERENCE_EXCEEDANCE_REQUIRES_CONFIRMATION') {
        setServerExcess(err.context?.preferenceExcessDays || []); setAllowPreferenceExceedance(false); setAttempt(null)
      }
      setError(`${err.message}${err.context?.excessMinutes ? ` Vượt ${err.context.excessMinutes} phút.` : ''}${err.context?.shortfallMinutes ? ` Thiếu ${err.context.shortfallMinutes} phút.` : ''}`)
    } finally { if (revision.current.isCurrent(ticket)) setBusy(false) }
  }

  return <section className="goal-detail" aria-label="Bản nháp kế hoạch AI">
    <h3>Kế hoạch ôn tập đề xuất</h3>
    <p>Mức mong muốn cho môn này: {goal.minutesPerDay} phút/ngày. AI có thể đề xuất nhiều hơn khi quỹ chung còn đủ; phần vượt cần bạn đồng ý. Tổng mọi môn không vượt quỹ chung, không xếp vào ngày thi. Chỉ xác nhận mới thêm việc vào lịch.</p>
    <div className="goal-item-actions">
      <button type="button" disabled={pending} onClick={() => load(true)}>{pending ? 'Đang sinh lịch…' : draft ? 'Sinh lại bản nháp AI' : 'Tạo bản nháp AI'}</button>
      <button type="button" disabled={pending} onClick={() => load(false)}>Xem đề xuất gần nhất</button>
      {onViewSchedule && <button type="button" disabled={pending} onClick={onViewSchedule}>Xem lịch đã lưu</button>}
    </div>
    {backlogs.length > 0 && <p role="status">Kế hoạch chưa bao phủ hết nội dung: còn việc đang chờ xếp. Tiến độ phía trên chỉ tính các việc đã đưa vào lịch học.</p>}
    {backlogs.length > 0 && <details><summary>Phần còn lại chưa đưa vào lịch ({backlogs.reduce((sum, value) => sum + value.proposal.tasks.length + value.proposal.unscheduled.length, 0)} việc)</summary>
      <p>Phần này chỉ có khi bạn đã chọn lưu một phần kế hoạch. Các việc còn lại được giữ ở đây để xếp tiếp, chưa phải buổi học trong lịch.</p>
      <button type="button" disabled={pending} onClick={openPending}>Tải lại phần còn lại</button>
      {backlogs.map(value => <button key={value.id} type="button" disabled={pending} onClick={() => { adopt(value); setError(''); setMessage(draftState(value).message) }}>Mở phần còn lại · {value.proposal.tasks.length + value.proposal.unscheduled.length} việc</button>)}
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
      {draft.proposal.scope?.respectPreferences && <p role="status">Đang giữ mức mong muốn từng ngày cho môn này. Phần chưa đủ chỗ được giữ lại, không mất nội dung.</p>}
      {excessDays.length > 0 && <section className="plan-shortage" aria-label="Đề xuất vượt mức mong muốn">
        <h4>{goal.subject}: đề xuất nhiều hơn mức mong muốn</h4>
        <ul>{excessDays.map(day => <li key={day.date}>{day.date}: {day.plannedMinutes} phút, mức mong muốn {day.preferredMinutes} phút — vượt {day.excessMinutes} phút (tính cả việc đã lưu của môn).{day.sharedBudgetMinutes !== undefined && <> Tổng mọi môn theo bản nháp: {day.sharedPlannedMinutes}/{day.sharedBudgetMinutes} phút.</>}</li>)}</ul>
        <p>Quỹ chung của tất cả môn vẫn là giới hạn bắt buộc và được kiểm tra lại khi lưu. Mức mong muốn và lịch đã lưu không tự thay đổi.</p>
        <label><input type="checkbox" checked={allowPreferenceExceedance} disabled={pending} onChange={e => { setAllowPreferenceExceedance(e.target.checked); setAttempt(null) }} /> Tôi đồng ý phần vượt mức mong muốn nêu trên.</label>
        {draft.proposal.scope && <button type="button" disabled={pending} onClick={() => reschedule(true)}>Giữ mức mong muốn và xếp lại</button>}
        <p>Xếp lại dùng việc và ước lượng của bản nháp đã lưu; việc không còn ngày phù hợp trước ngày thi được giữ ở phần chưa xếp.</p>
      </section>}
      {budgetNotice && <p role="status" className="plan-shortage-hint">Quỹ thời gian chung đã thay đổi sau khi bản nháp này được xếp, nên các số “Đã xếp” và “Cần thêm” bên dưới tính theo quỹ cũ. Bạn đã chỉnh bản nháp nên chưa tự xếp lại; hãy chọn “Xếp lại theo quỹ phút đã lưu” để cập nhật (các chỉnh sửa chưa lưu sẽ mất).</p>}
      <p>Bản nháp theo phiên bản v{draft.goalVersion}. Nếu mục tiêu hoặc công việc đã thay đổi, hãy tạo lại bản nháp.</p>
      {draft.proposal.summary?.alreadyScheduledCount > 0 && <p role="status">Đã bỏ qua {draft.proposal.summary.alreadyScheduledCount} công việc trùng với lịch hiện có; chỉ đề xuất việc mới bên dưới.</p>}
      {draft.proposal.scope && <>
        {heldBackCount > 0 && <p role="status" className="plan-shortage-hint">Bạn đã bỏ {heldBackCount} việc khỏi lần lưu này. Chúng không bị xóa: được giữ ở phần chưa xếp, chưa phải buổi học trong lịch, và bạn mở lại được sau khi lưu.</p>}
        {serverRemaining > 0 && <section className="plan-shortage" aria-label="Điều chỉnh kế hoạch chưa đủ thời gian">
          <h4>{draft.proposal.scope.respectPreferences ? 'Chưa xếp hết theo mức mong muốn' : review.shortfallMinutes > 0 ? 'Thời gian hiện tại chưa đủ để ôn hết nội dung' : 'Còn công việc chưa có ngày học'}</h4>
          <p role="status">Đã xếp <strong>{review.scheduled.length}/{items.length} việc</strong>. Còn <strong>{review.remaining.length} việc</strong> chưa có ngày học.</p>
          {review.shortfallMinutes > 0 ? <p>Cần thêm khoảng <strong>{review.shortfallMinutes} phút</strong> theo ước lượng hiện tại.</p>
            : <p>Tổng quỹ phút có thể đủ, nhưng cần bố trí lại từng ngày để mỗi việc có đủ thời gian. Bạn cũng có thể chọn ngày học thủ công bên dưới.</p>}
          {draft.proposal.scope.sharedBudgetLimitedDays > 0 ? <p className="plan-shortage-cause">Một số ngày đang bị giới hạn bởi <strong>quỹ thời gian chung của tất cả môn</strong>. Chỉ tăng phút riêng của mục tiêu có thể chưa đủ.</p>
            : <p className="plan-shortage-cause">AI ưu tiên mức mong muốn của môn; quỹ chung là giới hạn tổng bắt buộc. Nếu quỹ chung thấp, chỉ tăng mức mong muốn có thể chưa đủ. {onAdjustSharedBudget && <button type="button" disabled={pending} onClick={onAdjustSharedBudget}>Xem quỹ chung</button>}</p>}
          <div className="plan-shortage-actions">
            <div><button className="plan-shortage-primary" type="button" disabled={pending} onClick={() => draft.proposal.scope.sharedBudgetLimitedDays > 0 && onAdjustSharedBudget ? onAdjustSharedBudget() : onAdjust?.('time')}>Điều chỉnh thời gian</button>
              <small>{draft.proposal.scope.sharedBudgetLimitedDays > 0 ? 'Mở quỹ chung để xem thời gian còn lại của tất cả môn.' : 'Chỉnh số phút mỗi ngày dành cho mục tiêu này.'}</small></div>
            <div><button type="button" disabled={pending} onClick={() => onAdjust?.('topics')}>Chọn lại chủ đề</button><small>Giảm phạm vi ôn rồi tạo đề xuất AI mới.</small></div>
            <div><button type="submit" disabled={pending || review.scheduled.length === 0 || needsPreferenceConsent}>Lưu phần đã xếp</button><small>{review.remaining.length} việc còn lại được giữ để xếp tiếp; kế hoạch chưa bao phủ hết nội dung.</small></div>
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
        {task.heldBack && <p role="status">Đã bỏ khỏi lần lưu này; việc được giữ ở phần chưa xếp, không bị xóa.</p>}
        {!task.scheduledDate && !task.heldBack && <p role="status">Chưa xếp được: {task.reason || 'Hãy chọn một ngày còn đủ phút trước ngày thi.'}</p>}
        <label>Tiêu đề<input aria-label={`Tiêu đề công việc ${index + 1}`} value={task.title} required maxLength={200} onChange={e => edit(index, 'title', e.target.value)} /></label>
        <label>Ngày học<input type="date" aria-label={`Ngày học công việc ${index + 1}`} min={goal.startDate} value={task.scheduledDate || ''} onChange={e => edit(index, 'scheduledDate', e.target.value)} /></label>
        <label>Số phút<input type="number" aria-label={`Số phút công việc ${index + 1}`} required min={1} max={480} step={1} value={task.estimatedMinutes} onChange={e => edit(index, 'estimatedMinutes', e.target.value)} /></label>
        {!draft.proposal.scope && <button type="button" onClick={() => { setItems(previous => previous.filter((_, i) => i !== index)); setAttempt(null) }}>Bỏ công việc này</button>}
        {draft.proposal.scope && task.scheduledDate && <button type="button" data-focus-id={`hold-${index}`} onClick={() => holdBack(index)}>Bỏ khỏi lần lưu này</button>}
        {draft.proposal.scope && task.heldBack && task.previousDate && <button type="button" data-focus-id={`put-${index}`} onClick={() => putBack(index)}>Đưa lại vào lịch</button>}
      </fieldset>)}
      {draft.proposal.scope && <section aria-label="Việc bạn thêm" className="goal-field">
        <h4>Việc bạn thêm</h4>
        <p>Thêm việc ôn của riêng bạn vào bản nháp. Việc này chỉ vào lịch khi bạn xác nhận và được kiểm tra với quỹ chung như các việc khác.</p>
        {extras.map((extra, index) => <fieldset key={extra.key} disabled={pending} className="goal-field">
          <legend>Việc bạn thêm {index + 1}</legend>
          <label>Chủ đề<select aria-label={`Chủ đề việc bạn thêm ${index + 1}`} value={extra.topicId} onChange={e => editExtra(extra.key, 'topicId', e.target.value)}>
            {draft.proposal.scope.selectedTopics.map(topic => <option key={topic.id} value={topic.id}>{topic.title}</option>)}</select></label>
          <label>Tiêu đề<input data-focus-id={`extra-${index + 1}`} aria-label={`Tiêu đề việc bạn thêm ${index + 1}`} value={extra.title} required maxLength={200} onChange={e => editExtra(extra.key, 'title', e.target.value)} /></label>
          <label>Ngày học<input type="date" aria-label={`Ngày học việc bạn thêm ${index + 1}`} min={goal.startDate} required value={extra.scheduledDate} onChange={e => editExtra(extra.key, 'scheduledDate', e.target.value)} /></label>
          <label>Số phút<input type="number" aria-label={`Số phút việc bạn thêm ${index + 1}`} required min={1} max={480} step={1} value={extra.estimatedMinutes} onChange={e => editExtra(extra.key, 'estimatedMinutes', e.target.value)} /></label>
          <button type="button" onClick={() => removeExtra(extra.key)}>Xóa việc đã thêm</button>
        </fieldset>)}
        <button type="button" data-focus-id="add-extra" disabled={pending || extras.length >= 50} onClick={addExtra}>Thêm việc</button>
      </section>}
      <div className="goal-item-actions">
        <button type="submit" disabled={pending || review.scheduled.length === 0 || needsPreferenceConsent}>{review.remaining.length ? 'Lưu phần đã xếp' : 'Xác nhận và lưu kế hoạch'}</button>
        <button type="button" disabled={pending} onClick={() => { setDraft(null); setMessage('Đã đóng bản nháp; lịch học không thay đổi.') }}>Đóng bản nháp</button>
      </div>
      {(review.remaining.length > 0 || extras.length > 0) && <p className="plan-shortage-hint">Lưu {review.scheduled.length + extras.length} việc vào lịch{extras.length > 0 ? ` (gồm ${extras.length} việc bạn thêm)` : ''}.{review.remaining.length > 0 ? ` ${review.remaining.length} việc còn lại được giữ để xếp tiếp.` : ''}</p>}
    </form>}
  </section>
}
