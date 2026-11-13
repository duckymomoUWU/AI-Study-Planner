import { useEffect, useState } from 'react'
import { calendarSyncStatus, retryCalendarSync } from '../lib/googleCalendar.js'
import './CalendarSyncPanel.css'

const labels = { PENDING: 'Chờ đồng bộ', RETRY: 'Đang chờ thử lại', BLOCKED: 'Cần điều chỉnh', IN_SYNC: 'Đã đồng bộ', DELETED: 'Đã xóa trên Google' }
export default function CalendarSyncPanel({ revision, onOpenCalendar }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    const controller = new AbortController()
    async function load() {
      try { const value = await calendarSyncStatus({ signal: controller.signal }); if (active) { setData(value); setError('') } }
      catch (err) { if (active && err.name !== 'AbortError') setError(err.message) }
    }
    load()
    const timer = window.setInterval(load, 15000)
    return () => { active = false; controller.abort(); window.clearInterval(timer) }
  }, [revision, reload])
  async function retry() {
    setBusy(true); setError('')
    try { setData(await retryCalendarSync()); setReload(value => value + 1) }
    catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  if (!error && (!data || data.items.length === 0)) return null
  return <section className="calendar-sync" aria-label="Đồng bộ thay đổi với Google Calendar">
    <h2>Đồng bộ Google Calendar</h2>
    {error && <p role="alert">{error}</p>}
    {data && <>
      <p role="status">{data.pending} thay đổi đang chờ · {data.blocked} cần điều chỉnh · {data.synced} đã cập nhật · {data.deleted} đã xóa.</p>
      {(data.pending > 0 || data.blocked > 0) && <p>Thay đổi đã lưu trong app. Khi chưa đồng bộ xong, Google có thể vẫn hiện lịch cũ. Nếu ngày mới không đủ giờ, hãy đổi ngày/giờ học rồi thử lại.</p>}
      <details><summary>Xem thay đổi gần đây (tối đa 100)</summary><ul>{data.items.map(item => <li key={item.taskId}>
        <strong>{item.title}</strong> · {item.operation === 'DELETE' ? 'Xóa sự kiện' : 'Cập nhật sự kiện'} · {labels[item.status] || item.status}
        {item.message && <p>{item.message}</p>}
      </li>)}</ul></details>
    </>}
    <div><button type="button" disabled={busy} onClick={retry}>{busy ? 'Đang gửi…' : 'Thử đồng bộ lại'}</button>
      <button type="button" disabled={busy} onClick={() => setReload(value => value + 1)}>Tải trạng thái</button>
      {onOpenCalendar && <button type="button" onClick={onOpenCalendar}>Kết nối / chỉnh giờ học</button>}</div>
    <small>Đồng bộ một chiều từ app. App cập nhật sự kiện đã xuất; không nhập thay đổi từ Google về ứng dụng.</small>
  </section>
}
