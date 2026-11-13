import { useEffect, useRef, useState } from 'react'
import { completeSessionTask, loadSessionTask } from '../lib/sessionCompletion.js'

export default function SessionTaskCompletion({ session, onLogin, onCompleted }) {
  const [state, setState] = useState({ status: 'loading' })
  const [revision, setRevision] = useState(0)
  const inFlight = useRef(false)
  const mounted = useRef(true)
  const { goalId, taskId, status: sessionStatus } = session

  useEffect(() => {
    mounted.current = true
    const c = new AbortController()
    loadSessionTask({ status: sessionStatus, goalId, taskId }, { signal: c.signal }).then(task => {
      if (!c.signal.aborted) setState({ status: !task || task.completed ? 'hidden' : 'ready', task })
    }).catch(error => {
      if (!c.signal.aborted) setState({ status: error.status === 404 ? 'hidden' : 'loadError', error })
    })
    return () => { mounted.current = false; c.abort() }
  }, [goalId, taskId, sessionStatus, revision])

  async function confirm() {
    if (inFlight.current) return
    inFlight.current = true
    setState(previous => ({ status: 'saving', task: previous.task }))
    try {
      const task = await completeSessionTask(session)
      if (mounted.current) {
        if (task) onCompleted?.(task)
        setState({ status: task ? 'saved' : 'hidden' })
      }
    } catch (error) {
      if (mounted.current) setState(previous => ({ status: error.status === 404 ? 'removed' : 'saveError', task: previous.task, error }))
    } finally { inFlight.current = false }
  }

  if (state.status === 'hidden' || state.status === 'dismissed') return null
  if (state.status === 'loading') return <p className="timer-completion-status" role="status">Đang kiểm tra trạng thái công việc…</p>
  if (state.status === 'saved') return <p className="timer-completion-success" role="status">Đã đánh dấu hoàn thành công việc. Bạn có thể xem lại trong Lịch học.</p>
  if (state.status === 'removed') return <p className="timer-completion-status" role="status">Công việc không còn khả dụng. Phiên học của bạn vẫn được lưu.</p>
  if (state.status === 'loadError') return <div className="timer-completion"><p role="alert">Phiên học đã lưu. Chưa kiểm tra được trạng thái công việc.</p><button type="button" className="button button--secondary" onClick={() => setRevision(value => value + 1)}>Thử kiểm tra lại</button>{state.error.status === 401 && <button type="button" className="button button--primary" onClick={onLogin}>Đăng nhập lại</button>}</div>
  return <section className="timer-completion" aria-labelledby="timer-completion-title">
    <h3 id="timer-completion-title">Bạn đã hoàn thành công việc này chưa?</h3>
    <p className="timer-completion-task">{state.task?.title || session.taskTitle}</p>
    {state.status === 'saveError' && <p role="alert">Chưa xác nhận được việc đánh dấu hoàn thành. Phiên học vẫn được lưu; bạn có thể thử lại.</p>}
    <div className="timer-completion-actions"><button type="button" className="button button--primary" disabled={state.status === 'saving'} onClick={confirm}>{state.status === 'saving' ? 'Đang lưu…' : state.status === 'saveError' ? 'Thử đánh dấu lại' : 'Đánh dấu hoàn thành'}</button><button type="button" className="button button--secondary" disabled={state.status === 'saving'} onClick={() => setState({ status: 'dismissed' })}>Học tiếp sau</button>{state.error?.status === 401 && <button type="button" className="button button--secondary" onClick={onLogin}>Đăng nhập lại</button>}</div>
  </section>
}
