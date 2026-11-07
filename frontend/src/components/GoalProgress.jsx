import { useEffect, useState } from 'react'
import { describeProgress, describeProgressError, getGoalProgress } from '../lib/progress.js'
import { Progress } from './ui/progress.jsx'
import './GoalProgress.css'

/**
 * Progress of one goal: completed / total tasks, a bar, and how many are left. It loads its own data and loads
 * again when `version` changes (any change of the goal's tasks bumps the goal version).
 */
export default function GoalProgress({ goalId, version }) {
  const [state, setState] = useState({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    getGoalProgress(goalId, { signal: controller.signal })
      .then(data => {
        if (!controller.signal.aborted) setState({ status: 'ready', view: describeProgress(data) })
      })
      .catch(error => {
        if (!controller.signal.aborted && error.name !== 'AbortError') {
          setState({ status: 'error', message: describeProgressError(error) })
        }
      })
    return () => controller.abort()
  }, [goalId, version])

  if (state.status === 'loading') {
    return <p className="goal-progress-note" role="status">Đang tải tiến độ…</p>
  }
  if (state.status === 'error') {
    return <p className="goal-progress-note goal-progress-note--error" role="alert">{state.message}</p>
  }

  const { view } = state
  return (
    <div className="goal-progress">
      <div className="goal-progress-top">
        <span className="goal-progress-label">Tiến độ</span>
        <span className="goal-progress-value">{view.label} việc</span>
      </div>
      <Progress value={view.percent} aria-label={`Tiến độ: ${view.completed} trên ${view.total} công việc`} />
      <div className="goal-progress-badges">
        <span className="goal-progress-pill goal-progress-pill--done">{view.completed} hoàn thành</span>
        <span className="goal-progress-pill">{view.remaining} còn lại</span>
        {view.isEmpty && <span className="goal-progress-note">Chưa có công việc.</span>}
      </div>
    </div>
  )
}
