import { useRef, useState } from 'react'
import { updateTask } from '../lib/tasks.js'
import { formatStudyTime } from '../lib/taskStudyTime.js'
import { describeTaskError } from '../lib/taskErrors.js'
import './TaskTimeDetails.css'

export default function TaskTimeDetails({ task, total, error: loadError, onSaved, onReload }) {
  const [editing, setEditing] = useState(false)
  const [minutes, setMinutes] = useState(String(task.estimatedMinutes))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const flight = useRef(false)
  async function save(event) {
    event.preventDefault()
    if (flight.current) return
    const value = Number(minutes)
    if (!Number.isInteger(value) || value < 1 || value > 480) { setError('Nhập số nguyên từ 1 đến 480 phút.'); return }
    flight.current = true; setBusy(true); setError('')
    try {
      const updated = await updateTask(task.id, { estimatedMinutes: value })
      setEditing(false); onSaved?.(updated)
    } catch (e) {
      const info = describeTaskError(e)
      setError(info.message)
      if (info.reload) onReload?.()
    }
    finally { flight.current = false; setBusy(false) }
  }
  return <div className="task-time-details">
    <p>Dự kiến <strong>{task.estimatedMinutes} phút</strong> · Đã học <strong>{total ? formatStudyTime(total.totalFocusSeconds) : loadError ? 'chưa tải được' : 'đang tải…'}</strong></p>
    {!editing ? <button type="button" onClick={() => { setMinutes(String(task.estimatedMinutes)); setError(''); setEditing(true) }}>Điều chỉnh thời lượng</button> : <form onSubmit={save}>
      <label htmlFor={`estimate-${task.id}`}>Thời lượng dự kiến (phút)</label>
      <div><input id={`estimate-${task.id}`} type="number" min="1" max="480" step="1" value={minutes} disabled={busy} onChange={event => setMinutes(event.target.value)} required /><button type="submit" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu thời lượng'}</button><button type="button" disabled={busy} onClick={() => setEditing(false)}>Hủy</button></div>
      <small>Đây là tổng thời lượng dự kiến của công việc, không phải thời gian còn lại. App sẽ kiểm tra quỹ chung của ngày học.</small>
      {error && <p role="alert">{error}</p>}
    </form>}
  </div>
}
