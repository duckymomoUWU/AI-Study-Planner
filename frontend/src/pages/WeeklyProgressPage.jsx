import { useEffect, useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card.jsx'
import { Progress } from '../components/ui/progress.jsx'
import {
  describeWeeklyError, describeWeeklyProgress, getWeeklyProgress, shiftWeek, startOfWeek, todayString,
  WeeklyProgressApiError,
} from '../lib/weekly-progress.js'
import './WeeklyProgressPage.css'

function shortDate(dateStr) {
  const [, month, day] = dateStr.split('-')
  return `${day}/${month}`
}

export default function WeeklyProgressPage({ onBack, onNavigateDaily, onNavigateAuth }) {
  const [anchor, setAnchor] = useState(() => todayString())
  const [state, setState] = useState({ status: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)

  const currentWeekStart = startOfWeek(todayString())
  const selectedWeekStart = startOfWeek(anchor)
  const isCurrentWeek = selectedWeekStart === currentWeekStart

  useEffect(() => {
    const controller = new AbortController()
    getWeeklyProgress(selectedWeekStart, { signal: controller.signal })
      .then(data => {
        if (!controller.signal.aborted) setState({ status: 'ready', view: describeWeeklyProgress(data) })
      })
      .catch(error => {
        if (controller.signal.aborted || error.name === 'AbortError') return
        const unauthenticated = error instanceof WeeklyProgressApiError && error.status === 401
        setState({ status: unauthenticated ? 'unauthenticated' : 'error', message: describeWeeklyError(error) })
      })
    return () => controller.abort()
  }, [selectedWeekStart, reloadKey])

  function goToWeek(date) {
    setState({ status: 'loading' })
    setAnchor(date)
  }

  const view = state.status === 'ready' ? state.view : null

  return (
    <section className="weekly-page" aria-labelledby="weekly-title">
      <div className="weekly-heading">
        <div>
          <p className="eyebrow">Tiến độ</p>
          <h1 id="weekly-title">Tiến độ theo tuần</h1>
          <p>Số việc đã xong và tổng số việc của từng tuần, để theo dõi nhịp học của bạn.</p>
        </div>
        <div className="weekly-heading-actions">
          <button className="button button--secondary" type="button" onClick={onBack}>← Tổng quan</button>
          {onNavigateDaily && (
            <button className="button button--primary" type="button" onClick={onNavigateDaily}>Xem lịch học →</button>
          )}
        </div>
      </div>

      <div className="weekly-nav" role="group" aria-label="Chọn tuần">
        <div className="weekly-nav-buttons">
          <button className="button button--secondary" type="button"
            onClick={() => goToWeek(shiftWeek(selectedWeekStart, -1))}>← Tuần trước</button>
          <button className={`button ${isCurrentWeek ? 'button--primary' : 'button--secondary'}`} type="button"
            onClick={() => goToWeek(todayString())}>Tuần này</button>
          <button className="button button--secondary" type="button"
            onClick={() => goToWeek(shiftWeek(selectedWeekStart, 1))}>Tuần sau →</button>
        </div>
        <div className="weekly-range">
          <strong>{view ? view.range : '…'}</strong>
          {isCurrentWeek && <span className="weekly-pill">Tuần hiện tại</span>}
        </div>
      </div>

      {state.status === 'unauthenticated' && (
        <div className="weekly-alert weekly-alert--auth" role="alert">
          <div><strong>Chưa đăng nhập:</strong> {state.message}</div>
          {onNavigateAuth && (
            <button className="button button--primary" type="button" onClick={onNavigateAuth}>Đăng nhập ngay →</button>
          )}
        </div>
      )}
      {state.status === 'error' && (
        <div className="weekly-alert weekly-alert--error" role="alert">
          <div>{state.message}</div>
          <button className="button button--secondary" type="button" onClick={() => setReloadKey(k => k + 1)}>Thử lại</button>
        </div>
      )}

      {state.status === 'loading' && <p className="weekly-status" role="status">Đang tải tiến độ tuần…</p>}

      {view && (
        <div className="weekly-grid">
          <Card className="weekly-summary">
            <CardHeader>
              <CardDescription>Hoàn thành / tổng số việc</CardDescription>
              <CardTitle className="weekly-total">{view.label} <span>việc</span></CardTitle>
            </CardHeader>
            <CardContent>
              <Progress value={view.percent} aria-label={`Tiến độ tuần: ${view.completed} trên ${view.total} công việc`} />
              <div className="weekly-pills">
                <span className="weekly-pill weekly-pill--done">{view.completed} hoàn thành</span>
                <span className="weekly-pill">{view.remaining} còn lại</span>
                <span className="weekly-percent">{view.percent}%</span>
              </div>
              {view.isEmpty && (
                <p className="weekly-empty">Chưa có công việc nào trong tuần này.</p>
              )}
            </CardContent>
          </Card>

          <Card className="weekly-days">
            <CardHeader>
              <CardTitle>Theo từng ngày</CardTitle>
              <CardDescription>Thứ Hai đến Chủ Nhật</CardDescription>
            </CardHeader>
            <CardContent>
              <ul className="weekly-day-list">
                {view.days.map(day => (
                  <li key={day.date} className="weekly-day">
                    <span className="weekly-day-name">{day.label}</span>
                    <span className="weekly-day-date">{shortDate(day.date)}</span>
                    <Progress value={day.percent} aria-label={`${day.label} ${shortDate(day.date)}: ${day.completed} trên ${day.total}`} />
                    <span className="weekly-day-count">{day.completed}/{day.total}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      )}

      <p className="weekly-note">Tiến độ chỉ đếm số công việc đã đánh dấu hoàn thành, không đánh giá mức độ hiểu bài.</p>
    </section>
  )
}
