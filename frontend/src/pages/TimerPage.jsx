import { useCallback, useEffect, useRef, useState } from 'react'
import { getTodayString, listTasksByDate } from '../lib/tasks.js'
import { getActiveSession, getStudySession, listStudySessions, startStudySession, actOnStudySession, remainingFocus, formatSeconds, sessionError } from '../lib/studySessions.js'
import './TimerPage.css'

const labels = { RUNNING: 'Đang tập trung', PAUSED: 'Đang tạm dừng', COMPLETED: 'Đã lưu phiên học', CANCELLED: 'Đã hủy phiên học' }
export default function TimerPage({ currentUser, initialTaskId, onLogin, onSchedule }) {
  const [date, setDate] = useState(getTodayString)
  const [tasks, setTasks] = useState([])
  const [taskId, setTaskId] = useState(initialTaskId || '')
  const [taskError, setTaskError] = useState('')
  const [snapshot, setSnapshot] = useState(null)
  const [history, setHistory] = useState([])
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(null)
  const [cancelConfirm, setCancelConfirm] = useState(false)
  const [breakEnd, setBreakEnd] = useState(null)
  const [now, setNow] = useState(() => performance.now())
  const [receivedAt, setReceivedAt] = useState(() => performance.now())
  const syncGeneration = useRef(0)
  const current = useRef(null)
  const inFlight = useRef(false)
  const mounted = useRef(true)
  const active = snapshot && ['RUNNING', 'PAUSED'].includes(snapshot.status)

  const accept = useCallback(session => {
    setReceivedAt(performance.now()); current.current = session
    setSnapshot(session); setNow(performance.now())
  }, [])

  const sync = useCallback(async (signal) => {
    if (!currentUser || inFlight.current) return
    const generation = ++syncGeneration.current
    setError(''); setStatus('loading')
    try {
      let s = await getActiveSession({ signal })
      if (!s && current.current?.id) s = await getStudySession(current.current.id, { signal })
      const rows = await listStudySessions({ signal })
      if (signal?.aborted || !mounted.current || generation !== syncGeneration.current || inFlight.current) return
      accept(s); setHistory(rows); setPending(null); setStatus('ready')
    } catch (e) {
      if (signal?.aborted || !mounted.current || generation !== syncGeneration.current || inFlight.current) return
      setStatus(e.status === 401 ? 'unauthenticated' : 'error'); setError(sessionError(e))
    }
  }, [currentUser, accept])

  useEffect(() => {
    mounted.current = true
    const c = new AbortController(); sync(c.signal)
    return () => { mounted.current = false; c.abort() }
  }, [sync])

  useEffect(() => {
    if (!currentUser) return
    const c = new AbortController()
    listTasksByDate(date, { signal: c.signal }).then(result => {
      if (!c.signal.aborted) { setTasks(result.items); setTaskError('') }
    }).catch(() => { if (!c.signal.aborted) setTaskError('Không tải được công việc. Hãy chọn lại ngày hoặc mở lịch học.') })
    return () => c.abort()
  }, [date, currentUser])

  useEffect(() => {
    const timer = setInterval(() => setNow(performance.now()), 250)
    const focus = () => { if (document.visibilityState === 'visible') { setNow(performance.now()); sync() } }
    window.addEventListener('focus', focus); document.addEventListener('visibilitychange', focus)
    return () => { clearInterval(timer); window.removeEventListener('focus', focus); document.removeEventListener('visibilitychange', focus) }
  }, [sync])

  const send = useCallback(async operation => {
    if (inFlight.current) return
    syncGeneration.current++
    inFlight.current = true; setBusy(true); setError(''); setPending(operation)
    try {
      const session = operation.action === 'START'
        ? await startStudySession(operation.taskId, operation.key)
        : await actOnStudySession(operation.id, operation.action, operation.version, operation.key)
      if (!mounted.current) return
      accept(session); setPending(null); setStatus('ready'); setCancelConfirm(false); setBreakEnd(null)
      // A history read failure must not turn a confirmed write into an ambiguous retry.
      listStudySessions().then(rows => { if (mounted.current) setHistory(rows) }).catch(() => {})
    } catch (e) {
      if (!mounted.current) return
      if (e.status === 409) { setPending(null); setStatus('stale') }
      if (e.status === 404) setStatus('error')
      setError(sessionError(e)); if (e.status === 401) setStatus('unauthenticated')
    } finally { inFlight.current = false; if (mounted.current) setBusy(false) }
  }, [accept])

  const remaining = breakEnd ? Math.max(0, Math.ceil((breakEnd - now) / 1000)) : remainingFocus(snapshot, receivedAt, now)
  useEffect(() => {
    if (snapshot?.status === 'RUNNING' && remaining === 0 && !breakEnd && !pending && status === 'ready') {
      const finish = setTimeout(() => send({ id: snapshot.id, action: 'FINISH', version: snapshot.version, key: crypto.randomUUID() }), 0)
      return () => clearTimeout(finish)
    }
  }, [snapshot, remaining, breakEnd, pending, status, send])

  function action(type) {
    send(type === 'START' ? { action: type, taskId, key: crypto.randomUUID() }
      : { action: type, id: snapshot.id, version: snapshot.version, key: crypto.randomUUID() })
  }

  if (!currentUser || status === 'unauthenticated') return <section className="timer-page"><p className="eyebrow">MỘT KHOẢNG TẬP TRUNG</p><h1>Thời gian cho việc học.</h1><p>Đăng nhập để chọn công việc và mở lại phiên học của bạn.</p><button className="button button--primary" onClick={onLogin}>Đăng nhập</button></section>

  return <section className="timer-page" aria-labelledby="timer-title">
    <header className="timer-heading"><div><p className="eyebrow">MỘT VIỆC. MỘT KHOẢNG TẬP TRUNG.</p><h1 id="timer-title">Đến giờ học rồi.</h1><p>25 phút dành cho một việc. Sau đó, cho mình một khoảng nghỉ.</p></div><button className="button button--secondary" onClick={onSchedule}>Về lịch học ↗</button></header>
    {error && <div className="timer-alert" role="alert"><p>{error}</p><button className="button button--secondary" disabled={busy} onClick={() => sync()}>Tải lại trạng thái</button>{pending && <button className="button button--primary" disabled={busy} onClick={() => send(pending)}>Thử gửi lại yêu cầu</button>}</div>}
    <div className="timer-layout"><div className="timer-focus">
      <div className="timer-mode"><span className={!breakEnd ? 'timer-mode-active' : ''}>Tập trung · 25 phút</span><span className={breakEnd ? 'timer-mode-active' : ''}>Nghỉ · 5 phút</span></div>
      <p className="timer-state" role="status">{busy ? 'Đang xác nhận với máy chủ…' : status === 'loading' ? 'Đang tải phiên học…' : breakEnd ? remaining === 0 ? 'Đã hết giờ nghỉ' : 'Một khoảng nghỉ cho bạn' : labels[snapshot?.status] || 'Sẵn sàng khi bạn muốn'}</p>
      <div className="timer-dial" style={{ '--timer-progress': `${100 * (1 - remaining / (breakEnd ? 300 : 1500))}%` }}><div><span className="timer-count" role="timer" aria-live="off" aria-label={`Còn ${formatSeconds(remaining)}`}>{formatSeconds(remaining)}</span><small>{breakEnd ? 'thả lỏng một chút' : 'chỉ cần bắt đầu một việc'}</small></div></div>
      <h2>{active ? snapshot.taskTitle : breakEnd ? 'Hít thở. Đứng dậy. Uống nước.' : snapshot?.status === 'COMPLETED' ? 'Bạn đã dành thời gian cho việc học.' : 'Một bước nhỏ cũng là tiến bộ.'}</h2>
      {active && <p>{snapshot.subject}</p>}
      {snapshot?.status === 'COMPLETED' && !breakEnd && <p>Đã lưu {formatSeconds(snapshot.focusedSeconds)} tập trung. Công việc chưa tự được đánh dấu hoàn thành.</p>}
      {snapshot?.status === 'CANCELLED' && <p>Phiên hủy không được tính vào tổng thời gian học đã hoàn tất.</p>}
      <div className="timer-actions">
        {breakEnd ? <button className="button button--primary" onClick={() => setBreakEnd(null)}>Quay lại tập trung</button> : active ? <>
          <button className="button button--primary" disabled={busy || Boolean(pending) || status !== 'ready' || remaining === 0} onClick={() => action(snapshot.status === 'RUNNING' ? 'PAUSE' : 'RESUME')}>{snapshot.status === 'RUNNING' ? 'Tạm dừng' : 'Tiếp tục'}</button>
          <button className="button button--secondary" disabled={busy || Boolean(pending) || status !== 'ready'} onClick={() => action('FINISH')}>Kết thúc và lưu</button>
          <button className="timer-text-button" disabled={busy || Boolean(pending) || status !== 'ready'} onClick={() => setCancelConfirm(true)}>Hủy phiên</button>
        </> : <>
          <button className="button button--primary" disabled={!taskId || busy || Boolean(pending) || status !== 'ready'} onClick={() => action('START')}>Bắt đầu tập trung <span aria-hidden="true">→</span></button>
          {snapshot?.status === 'COMPLETED' && <button className="button button--secondary" onClick={() => { setBreakEnd(performance.now() + 300000); setNow(performance.now()) }}>Nghỉ 5 phút</button>}
        </>}
      </div>
      {cancelConfirm && <div className="timer-confirm" role="group" aria-label="Xác nhận hủy phiên"><p>Hủy phiên này? Thời gian của phiên sẽ không được cộng vào tổng đã học.</p><button className="button button--secondary" disabled={busy} onClick={() => setCancelConfirm(false)}>Giữ phiên học</button><button className="button button--secondary" disabled={busy} onClick={() => action('CANCEL')}>Đồng ý hủy phiên</button></div>}
      <p className="timer-footnote">Thời gian học được máy chủ xác nhận. Giờ nghỉ không cộng vào thời gian học.</p>
    </div><aside className="timer-side"><section className="timer-task-picker"><p className="eyebrow">DÀNH THỜI GIAN CHO</p><h2>Một công việc của bạn</h2><label htmlFor="timer-date">Ngày học</label><input id="timer-date" type="date" value={date} disabled={Boolean(active)} onChange={e => { setDate(e.target.value); setTaskId('') }} /><label htmlFor="timer-task">Công việc</label><select id="timer-task" value={taskId} disabled={Boolean(active)} onChange={e => setTaskId(e.target.value)}><option value="">Chọn công việc</option>{initialTaskId && !tasks.some(t => t.id === initialTaskId) && <option value={initialTaskId}>Công việc đã chọn từ lịch học</option>}{tasks.map(t => <option key={t.id} value={t.id}>{t.title}{t.completed ? ' · đã hoàn thành' : ''}</option>)}</select>{taskError && <p role="alert">{taskError}</p>}{!tasks.length && !taskError && <p>Chưa có công việc trong ngày này. Chọn ngày khác hoặc mở lịch học.</p>}<small>Bạn có thể ôn lại cả công việc đã hoàn thành.</small></section>
      <section className="timer-history"><p className="eyebrow">NHỮNG KHOẢNG ĐÃ DÀNH</p><h2>Phiên học gần đây</h2>{history.length ? <ul>{history.map(s => <li key={s.id}><strong>{s.taskTitle}</strong><span>{labels[s.status] || 'Phiên học'} · {formatSeconds(s.focusedSeconds)}</span><small>{new Date(s.startedAt).toLocaleString('vi-VN')}</small></li>)}</ul> : <p>{status === 'ready' ? 'Chưa có phiên học. Bắt đầu khi bạn sẵn sàng.' : 'Lịch sử sẽ hiện khi kết nối được dịch vụ phiên học.'}</p>}</section>
    </aside></div>
  </section>
}
