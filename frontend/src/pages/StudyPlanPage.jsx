import { useEffect, useState } from 'react'
import StudyBudgetPanel from '../components/StudyBudgetPanel.jsx'
import { listGoals } from '../lib/goals.js'
import { setTaskCompleted } from '../lib/tasks.js'
import { vietnamToday } from '../lib/goalPlanning.js'
import { loadStudyWeek, offsetDay, visibleStudyDays, weekDays } from '../lib/studyWeek.js'
import PlanDraftPanel from '../components/PlanDraftPanel.jsx'
import CalendarExportPanel from '../components/CalendarExportPanel.jsx'
import CalendarSyncPanel from '../components/CalendarSyncPanel.jsx'
import DailyTasksPage from './DailyTasksPage.jsx'
import './StudyPlanPage.css'
import TaskTimeDetails from '../components/TaskTimeDetails.jsx'
import useTaskStudyTimes from '../hooks/useTaskStudyTimes.js'

const dateLabel = day => new Intl.DateTimeFormat('vi-VN', { day: 'numeric', month: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${day}T12:00:00Z`))
const weekdayLabel = day => new Intl.DateTimeFormat('vi-VN', { weekday: 'long', timeZone: 'UTC' }).format(new Date(`${day}T12:00:00Z`))

export default function StudyPlanPage({ initialDate, onNavigateGoals, onNavigateAuth, onEditGoal, onNavigateTimer, ...dailyProps }) {
  const [anchor, setAnchor] = useState(() => initialDate || vietnamToday())
  const [days, setDays] = useState([])
  const [goals, setGoals] = useState([])
  const [goalId, setGoalId] = useState('')
  const [plannerId, setPlannerId] = useState('')
  const [showPlanner, setShowPlanner] = useState(false)
  const [showCalendar, setShowCalendar] = useState(() => typeof window !== 'undefined' && new URLSearchParams(window.location.search).has('calendar'))
  const [dailyDate, setDailyDate] = useState(null)
  const [revision, setRevision] = useState(0)
  const [loadedRequest, setLoadedRequest] = useState('')
  const [error, setError] = useState('')
  const [unauthenticated, setUnauthenticated] = useState(false)
  const [pending, setPending] = useState(null)
  const [notice, setNotice] = useState('')
  const [sharedBudgetFocus, setSharedBudgetFocus] = useState(0)
  const today = vietnamToday()
  const dates = weekDays(anchor)
  const start = dates[0]
  const requestKey = `${start}:${revision}`
  const loading = loadedRequest !== requestKey

  useEffect(() => {
    const controller = new AbortController()
    let current = true
    Promise.all([loadStudyWeek(start, { signal: controller.signal }), listGoals({ signal: controller.signal })])
      .then(([loaded, subjects]) => {
        if (!current) return
        setDays(loaded); setGoals(subjects); setError(''); setUnauthenticated(false)
      }).catch(err => {
        if (!current) return
        setDays([])
        setUnauthenticated(err.status === 401)
        setError(err.status === 401 ? 'Đăng nhập để xem lịch học của bạn.' : err.message || 'Không thể tải lịch học.')
      }).finally(() => { if (current) setLoadedRequest(requestKey) })
    return () => { current = false; controller.abort() }
  }, [start, revision, requestKey])

  const visible = visibleStudyDays(loading ? [] : days, goalId)
  const tasks = visible.flatMap(day => day.items)
  const studyTimes = useTaskStudyTimes(tasks, revision)
  const plannerGoal = goals.find(goal => goal.id === plannerId)
  const refresh = () => setRevision(value => value + 1)

  async function complete(task) {
    if (pending) return
    setPending(task.id); setError('')
    try { await setTaskCompleted(task.goalId, task.id, !task.completed); refresh() }
    catch (err) { setError(err.message || 'Không thể cập nhật công việc.') }
    finally { setPending(null) }
  }

  if (dailyDate) return <>
    {dailyProps.currentUser && <CalendarSyncPanel onOpenCalendar={() => { setDailyDate(null); setShowCalendar(true); refresh() }} />}
    <button className="study-back" type="button" onClick={() => { setDailyDate(null); refresh() }}>← Lịch học theo tuần</button>
    <DailyTasksPage key={dailyDate} {...dailyProps} initialDate={dailyDate} onNavigateGoals={onNavigateGoals} onNavigateAuth={onNavigateAuth} onNavigateTimer={onNavigateTimer} />
  </>

  return <section className="study-plan" aria-labelledby="study-plan-title">
    <header className="study-heading">
      <div><p className="study-eyebrow">KẾ HOẠCH ÔN TẬP</p><h1 id="study-plan-title">Lịch học</h1>
        <p>Một tuần rõ ràng. Tập trung từng buổi học.</p></div>
      <button type="button" className="study-primary" onClick={() => { setAnchor(today); setDailyDate(today) }}>Việc hôm nay ↗</button>
    </header>

    <div className="study-summary">
      <span><strong>{tasks.length}</strong> buổi học trong tuần</span>
      <span><strong>{tasks.reduce((sum, task) => sum + task.estimatedMinutes, 0)}</strong> phút dự kiến</span>
      <span><strong>{tasks.filter(task => task.completed).length}/{tasks.length}</strong> đã hoàn thành</span>
      <button type="button" disabled={loading || unauthenticated} onClick={() => setShowCalendar(value => !value)}>Đưa lên Google Calendar ↗</button>
      <button type="button" className="study-primary" disabled={loading || unauthenticated} onClick={() => {
        setShowPlanner(value => !value); setPlannerId(goalId || goals.find(goal => goal.examDate > today)?.id || '')
      }}>✦ Lên lịch từ mục tiêu</button>
    </div>

    {dailyProps.currentUser && <StudyBudgetPanel from={start} to={dates[6]} revision={revision} onSaved={refresh} focusRequest={sharedBudgetFocus} />}
    {dailyProps.currentUser && <CalendarSyncPanel revision={revision} onOpenCalendar={() => setShowCalendar(true)} />}
    {notice && <p className="study-notice" role="status">{notice}</p>}
    {showCalendar && <CalendarExportPanel key={`${start}:${goalId}`} from={start} goalId={goalId} onClose={() => setShowCalendar(false)} />}
    {showPlanner && <section className="study-planner" aria-label="Tạo lịch học AI">
      <div className="study-planner-top"><h2>Lên lịch ôn tập</h2><button type="button" onClick={() => setShowPlanner(false)}>Đóng</button></div>
      <p>Chọn mục tiêu, tạo hoặc mở lại bản nháp, rồi xác nhận để thêm buổi học vào lịch.</p>
      <label>Mục tiêu <select value={plannerId} onChange={event => setPlannerId(event.target.value)}>
        <option value="">Chọn mục tiêu cần ôn tập</option>
        {goals.filter(goal => goal.examDate > today).map(goal => <option key={goal.id} value={goal.id}>{goal.subject} · Thi {dateLabel(goal.examDate)}</option>)}
      </select></label>
      {plannerGoal && <PlanDraftPanel key={plannerGoal.id} goal={plannerGoal} onAdjust={section => onEditGoal ? onEditGoal(plannerGoal.id, section) : onNavigateGoals()}
        onAdjustSharedBudget={() => setSharedBudgetFocus(value => value + 1)} onViewSchedule={() => { setShowPlanner(false); refresh() }} onSaved={(result, firstDate) => {
        if (firstDate) setAnchor(firstDate)
        setNotice(`Đã thêm ${result.createdCount} buổi học vào lịch.`); setShowPlanner(false); refresh()
      }} />}
      {!goals.some(goal => goal.examDate > today) && <button className="study-primary" type="button" onClick={onNavigateGoals}>Tạo mục tiêu ôn tập</button>}
    </section>}

    <nav className="study-toolbar" aria-label="Điều hướng tuần">
      <div className="study-week-controls">
        <button type="button" aria-label="Tuần trước" onClick={() => setAnchor(offsetDay(start, -7))}>‹</button>
        <span>{dateLabel(start)} – {dateLabel(dates[6])}</span>
        <button type="button" aria-label="Tuần sau" onClick={() => setAnchor(offsetDay(start, 7))}>›</button>
        <button type="button" onClick={() => setAnchor(today)}>Hôm nay</button>
      </div>
      <label className="study-filter"><span>Môn học</span><select value={goalId} onChange={event => setGoalId(event.target.value)}>
        <option value="">Tất cả mục tiêu</option>
        {goals.map(goal => <option key={goal.id} value={goal.id}>{goal.subject} · {dateLabel(goal.examDate)}</option>)}
      </select></label>
    </nav>
    {error && <div className="study-error" role="alert"><p>{error}</p>
      <button type="button" onClick={unauthenticated ? onNavigateAuth : refresh}>{unauthenticated ? 'Đăng nhập' : 'Thử lại'}</button></div>}
    {loading ? <p className="study-empty" role="status">Đang tải lịch tuần…</p> : !error && <>
      {tasks.length === 0 && <div className="study-empty"><h2>Tuần này chưa có buổi học</h2>
        <p>Nếu vừa tạo mục tiêu, hãy mở bản nháp AI và xác nhận để lịch học được lưu. Bạn cũng có thể xem tuần khác hoặc thêm công việc theo ngày.</p>
        <button type="button" className="study-primary" onClick={() => {
          setShowPlanner(true); setPlannerId(goalId || goals.find(goal => goal.examDate > today)?.id || '')
        }}>Mở kế hoạch AI</button></div>}
      <div className="study-days">
        {visible.map(day => <section className={`study-day${day.date === today ? ' study-day--today' : ''}`} key={day.date} aria-label={dateLabel(day.date)}>
          <div className="study-date"><h2>{weekdayLabel(day.date)} {day.date === today && <span>Hôm nay</span>}</h2>
            <p>{dateLabel(day.date)}</p><p>{day.minutes} phút · {day.completed}/{day.items.length} xong</p>
            <button type="button" onClick={() => setDailyDate(day.date)}>Xem ngày ↗</button></div>
          <div className="study-sessions">{day.items.length === 0 ? <p className="study-rest">Chưa có buổi học · Bạn có thể nghỉ hoặc thêm công việc.</p> : day.items.map(task => {
            const goal = goals.find(item => item.id === task.goalId)
            return <article className={`study-session${task.completed ? ' study-session--done' : ''}`} key={task.id}>
              <span className="study-session-icon" aria-hidden="true">▦</span>
              <div className="study-session-copy"><h3>{task.title}</h3>
                <p>{goal?.subject || 'Mục tiêu học tập'}{task.topicTitle ? ` · ${task.topicTitle}` : ''}</p>
                {task.completed && <span>Đã hoàn thành</span>}
                <TaskTimeDetails task={task} total={studyTimes.totals[task.id]} error={studyTimes.error} onSaved={refresh} onReload={refresh} /></div>
              <button className={task.completed ? 'study-done' : 'study-primary'} type="button" disabled={Boolean(pending)}
                aria-label={`${task.completed ? 'Đánh dấu chưa xong' : 'Hoàn thành'}: ${task.title}`} onClick={() => complete(task)}>
                {pending === task.id ? 'Đang lưu…' : task.completed ? '✓ Đã xong' : 'Hoàn thành'}</button>
              {onNavigateTimer && <button type="button" onClick={() => onNavigateTimer(task)} aria-label={`Tập trung học: ${task.title}`}>◷ Tập trung</button>}
            </article>
          })}</div>
        </section>)}
      </div>
    </>}
  </section>
}
