import { useEffect, useState } from 'react'
import { listGoals } from '../lib/goals.js'
import { listTasksByDate } from '../lib/tasks.js'
import { vietnamToday } from '../lib/goalPlanning.js'
import { daysToExam, overviewSummary } from '../lib/overview.js'
import './OverviewPage.css'

const displayDate = day => new Intl.DateTimeFormat('vi-VN', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(new Date(`${day}T12:00:00Z`))

export default function OverviewPage({ currentUser, checkingAuth, onGoals, onSchedule, onLogin }) {
  const [now] = useState(() => new Date())
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  const [retry, setRetry] = useState(0)
  const today = vietnamToday(now)
  useEffect(() => {
    if (!currentUser) return
    let current = true
    const controller = new AbortController()
    Promise.all([listGoals({ signal: controller.signal }), listTasksByDate(today, { signal: controller.signal })])
      .then(([goals, tasks]) => {
        if (current) { setData({ goals, tasks: tasks.items }); setError(''); setExpired(false) }
      }).catch(err => {
        if (current) {
          setExpired(err.status === 401)
          setError(err.status === 401 ? 'Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại để xem kế hoạch của bạn.' : 'Chưa tải được kế hoạch học tập. Bạn có thể thử lại.')
        }
      })
    return () => { current = false; controller.abort() }
  }, [currentUser, today, retry])

  const summary = data ? overviewSummary(data.goals, data.tasks, today) : null
  const pendingTasks = data?.tasks.filter(task => !task.completed) || []
  const loading = checkingAuth || (currentUser && !data && !error)

  return <section className="overview-page" aria-labelledby="overview-title">
    <div className="overview-intro"><div><p className="eyebrow">KHÔNG GIAN HỌC TẬP CỦA BẠN</p>
      <h1 id="overview-title">Học từng chút.<br />Tiến xa mỗi ngày.</h1>
      <p>Một kế hoạch vừa sức, một việc cần làm rõ ràng.</p></div>
      <span className="overview-date">{new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Asia/Ho_Chi_Minh' }).format(now)}</span>
    </div>

    <section className="overview-focus" aria-labelledby="focus-title">
      <div className="overview-focus-copy"><span className="overview-kicker">BẮT ĐẦU TỪ HÔM NAY</span>
        <h2 id="focus-title">Dành một khoảng nhỏ<br />cho điều bạn muốn giỏi hơn.</h2>
        <p>{currentUser ? 'Xem buổi học tiếp theo, tập trung hoàn thành từng việc và giữ nhịp ôn tập của bạn.' : 'Thêm mục tiêu, tải đề cương và cùng AI lên kế hoạch ôn tập trước ngày thi.'}</p>
        <div className="overview-actions"><button className="button button--primary" type="button" onClick={currentUser ? onSchedule : onLogin}>{currentUser ? 'Mở lịch học' : 'Bắt đầu học tập'} <span aria-hidden="true">↗</span></button>
          <button className="overview-text-button" type="button" onClick={currentUser ? onGoals : onLogin}>{currentUser ? '+ Thêm mục tiêu' : 'Đăng nhập tài khoản'}</button></div>
      </div>
      <div className="overview-focus-side">
        {currentUser ? <><div className="overview-ring" style={{ '--completion': `${summary?.progress || 0}%` }}><div>
          <strong>{loading || error ? '—' : `${summary?.progress || 0}%`}</strong><span>hôm nay</span></div></div>
          <p>{loading ? 'Đang tải kế hoạch…' : error ? 'Chưa có dữ liệu' : summary?.total ? `${summary.completed}/${summary.total} công việc đã hoàn thành` : 'Chưa có công việc cho hôm nay'}</p></>
          : <div className="overview-guest-note"><span aria-hidden="true">✦</span><h3>Kế hoạch của bạn.<br />Nhịp học của bạn.</h3><p>AI đề xuất. Bạn kiểm tra và quyết định.</p></div>}
      </div>
    </section>

    {loading && <p className="overview-feedback" role="status">Đang tải thông tin học tập…</p>}
    {error && <div className="overview-feedback overview-feedback--error" role="alert"><p>{error}</p>
      <button type="button" className="overview-text-button" onClick={expired ? onLogin : () => setRetry(value => value + 1)}>{expired ? 'Đăng nhập lại' : 'Thử lại'}</button></div>}

    {currentUser && summary && !error && <>
      <div className="overview-stats" aria-label="Tóm tắt học tập">
        <article><span>Mục tiêu sắp thi</span><strong>{summary.upcoming.length}<small>mục tiêu</small></strong><p>Ngày thi từ hôm nay trở đi</p></article>
        <article><span>Cần làm hôm nay</span><strong>{summary.remaining}<small>công việc</small></strong><p>Đã hoàn thành {summary.completed}/{summary.total} công việc</p></article>
        <article><span>Thời gian còn lại hôm nay</span><strong>{summary.remainingMinutes}<small>phút học</small></strong><p>Theo công việc chưa hoàn thành</p></article>
      </div>

      <div className="overview-columns">
        <section className="overview-panel" aria-labelledby="overview-today"><header><div><span className="overview-kicker">GIỮ NHỊP MỖI NGÀY</span><h2 id="overview-today">Việc hôm nay</h2></div><button type="button" className="overview-text-button" onClick={onSchedule}>Xem lịch ↗</button></header>
          {pendingTasks.length ? <ul className="overview-task-list">{pendingTasks.slice(0, 4).map(task => <li key={task.id} style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span className="overview-task-marker" aria-hidden="true">○</span><div style={{ flex: 1 }}><h3>{task.title}</h3><p>{data.goals.find(goal => goal.id === task.goalId)?.subject || 'Mục tiêu học tập'}</p></div><span className="overview-minutes">{task.estimatedMinutes} phút</span>
            {onTimer && (
              <button
                type="button"
                className="button button--primary button--sm"
                style={{ minHeight: '30px', padding: '3px 8px', fontSize: '12px' }}
                onClick={() => onTimer(task.id)}
                title="Bắt đầu học Pomodoro"
              >
                ⏱ Bắt đầu học
              </button>
            )}
          </li>)}</ul> : <div className="overview-empty"><span aria-hidden="true">✧</span><h3>{summary.total ? 'Bạn đã xong việc hôm nay.' : 'Hôm nay còn trống.'}</h3><p>{summary.total ? 'Bạn có thể nghỉ ngơi hoặc xem trước buổi học tiếp theo.' : 'Mở lịch học để tạo hoặc xác nhận kế hoạch cho mục tiêu của bạn.'}</p><button className="overview-text-button" type="button" onClick={onSchedule}>Đến lịch học →</button></div>}
        </section>
        <section className="overview-panel" aria-labelledby="overview-exams"><header><div><span className="overview-kicker">CHUẨN BỊ TỪ SỚM</span><h2 id="overview-exams">Kỳ thi sắp tới</h2></div><button type="button" className="overview-text-button" onClick={onGoals}>Mục tiêu ↗</button></header>
          {summary.upcoming.length ? <ul className="overview-exam-list">{summary.upcoming.slice(0, 3).map(goal => {
            const days = daysToExam(today, goal.examDate)
            return <li key={goal.id}><div><h3>{goal.subject}</h3><p>{displayDate(goal.examDate)} · {goal.minutesPerDay} phút/ngày</p></div><span className={`overview-countdown${days <= 7 ? ' overview-countdown--soon' : ''}`}>{days === 0 ? 'Hôm nay' : `${days} ngày nữa`}</span></li>
          })}</ul> : <div className="overview-empty"><span aria-hidden="true">◎</span><h3>Đặt mục tiêu đầu tiên.</h3><p>Chọn môn học và ngày thi để bắt đầu lên kế hoạch.</p><button className="overview-text-button" type="button" onClick={onGoals}>+ Tạo mục tiêu</button></div>}
        </section>
      </div>
    </>}

    <section className="overview-guide" aria-labelledby="overview-guide-title"><div className="overview-guide-heading"><h2 id="overview-guide-title">Từ mục tiêu đến thói quen</h2><p>Ba bước cho một kế hoạch rõ ràng.</p></div>
      <div className="overview-steps">{[
        ['01', 'Chọn điều cần ôn', 'Thêm môn học, tải đề cương và chọn các chủ đề cần tập trung.'],
        ['02', 'Lên lịch vừa sức', 'Nhập thời gian có thể học, xem kế hoạch AI đề xuất và xác nhận.'],
        ['03', 'Tiến bộ từng ngày', 'Theo lịch học, đánh dấu việc đã xong và điều chỉnh khi cần.'],
      ].map(([number, title, text]) => <article key={number}><span>{number}</span><h3>{title}</h3><p>{text}</p></article>)}</div>
    </section>
  </section>
}
