import { useEffect, useRef, useState } from 'react'
import { calendarStatus, calendarList, connectCalendar, disconnectCalendar, saveCalendarSettings, previewCalendar, confirmCalendar, googleAuthorizationUrl, eventLink, calendarSelection } from '../lib/googleCalendar.js'
import './CalendarExportPanel.css'

const weekdays = ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật']
export default function CalendarExportPanel({ from, goalId, onClose }) {
  const [status, setStatus] = useState(null)
  const [calendars, setCalendars] = useState([])
  const [settings, setSettings] = useState(null)
  const [preview, setPreview] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [needsReconnect, setNeedsReconnect] = useState(false)
  const [busy, setBusy] = useState(false)
  const [revision, setRevision] = useState(0)
  const [failedCalendarIds, setFailedCalendarIds] = useState([])
  const live = useRef(true)
  useEffect(() => {
    live.current = true
    return () => { live.current = false }
  }, [])
  useEffect(() => {
    let current = true
    const controller = new AbortController()
    calendarStatus({ signal: controller.signal }).then(async value => {
      const list = value.connected ? await calendarList({ signal: controller.signal }) : []
      if (!current) return
      setStatus(value); setCalendars(list)
      const defaultCalendar = list.find(item => item.primary && item.writable) || list.find(item => item.writable)
      setSettings({ ...value.settings,
        targetCalendarId: value.settings.targetCalendarId || defaultCalendar?.id || '',
        busyCalendarIds: value.settings.busyCalendarIds.length ? value.settings.busyCalendarIds : list.map(item => item.id).slice(0, 49),
      })
      setError(''); setNeedsReconnect(false)
    }).catch(err => { if (current) { setError(err.message); setNeedsReconnect(['CALENDAR_RECONNECT', 'CALENDAR_PERMISSION_DENIED'].includes(err.code)) } })
    return () => { current = false; controller.abort() }
  }, [revision])

  function change(next) { setSettings(next); setPreview(null); setResult(null); setError(''); setFailedCalendarIds([]) }
  async function run(action) {
    if (busy) return
    setBusy(true); setError('')
    try { await action(); if (live.current) setFailedCalendarIds([]) } catch (err) { if (live.current) { setError(err.message); setFailedCalendarIds(err.context?.failedCalendarIds || []); setNeedsReconnect(['CALENDAR_RECONNECT', 'CALENDAR_PERMISSION_DENIED'].includes(err.code)) } }
    finally { if (live.current) setBusy(false) }
  }
  async function connect() {
    const response = await connectCalendar()
    if (live.current) window.location.assign(googleAuthorizationUrl(response.url))
  }
  function formatTime(instant, zone) {
    return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', timeZone: zone }).format(new Date(instant))
  }
  const canExport = preview && preview.slots.length > 0 && preview.slots.every(slot => slot.start)
  const selection = settings ? calendarSelection(settings, calendars) : null
  const invalidSelection = selection && (!selection.targetUsable || selection.missingBusyCalendarIds.length > 0)

  return <section className="calendar-export" aria-labelledby="calendar-export-title">
    <header><div><p className="study-eyebrow">GOOGLE CALENDAR</p><h2 id="calendar-export-title">Dành chỗ cho việc học</h2></div>
      <button type="button" disabled={busy} onClick={onClose}>Đóng</button></header>
    <p>Xếp giờ cho các công việc chưa hoàn thành trong tuần đang xem. Giữ nguyên ngày học, tránh lịch bận và giờ sinh hoạt của bạn.</p>
    <p>Sau khi xuất, sửa tên/ngày/thời lượng trong app sẽ cập nhật sự kiện tương ứng; xóa việc hoặc mục tiêu sẽ xóa sự kiện khi đồng bộ. Nếu ngày mới không còn chỗ, app báo cần điều chỉnh và giữ sự kiện cũ.</p>
    {error && <div role="alert" className="calendar-export-error"><p>{error}</p><button disabled={busy} type="button" onClick={() => setRevision(value => value + 1)}>Tải lại kết nối</button>
      {needsReconnect && <button disabled={busy} type="button" onClick={() => run(connect)}>Kết nối lại Google</button>}</div>}
    {new URLSearchParams(window.location.search).get('calendar') === 'error' && <p className="calendar-export-note" role="status">Chưa hoàn tất cấp quyền Google. Bạn có thể thử kết nối lại.</p>}
    {!status && !error && <p role="status">Đang kiểm tra kết nối Google…</p>}
    {status && !status.configured && <p className="calendar-export-note">Máy chủ chưa bật kết nối Google Calendar. Quản trị viên cần hoàn tất cấu hình Google OAuth trước khi bạn kết nối tài khoản.</p>}
    {status?.configured && !status.connected && <button className="study-primary" type="button" disabled={busy} onClick={() => run(connect)}>Kết nối Google Calendar</button>}
    {status?.connected && settings && <>
      <div className="calendar-export-connected"><span>✓ Đã kết nối Google Calendar</span>
        <button type="button" disabled={busy} onClick={() => {
          if (window.confirm('Ngắt kết nối Google? Các sự kiện đã xuất vẫn được giữ lại trên Google Calendar.')) run(async () => {
            await disconnectCalendar(); if (live.current) { setPreview(null); setResult(null); setRevision(value => value + 1) }
          })
        }}>Ngắt kết nối</button></div>
      <fieldset disabled={busy} className="calendar-export-settings"><legend>Thời gian dành cho việc học</legend>
        <div className="calendar-export-fields"><label>Lịch nhận buổi học<select value={settings.targetCalendarId} onChange={e => change({ ...settings, targetCalendarId: e.target.value })}>
          {settings.targetCalendarId && !selection.targetUsable && <option disabled value={settings.targetCalendarId}>Lịch đã chọn không còn quyền ghi / truy cập</option>}
          <option value="">Chọn lịch có quyền ghi</option>{calendars.filter(item => item.writable).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label>Múi giờ<select value={settings.timezone} onChange={e => change({ ...settings, timezone: e.target.value })}><option value="Asia/Ho_Chi_Minh">Việt Nam (UTC+7)</option>{settings.timezone !== 'Asia/Ho_Chi_Minh' && <option value={settings.timezone}>{settings.timezone}</option>}</select></label>
          <label>Nghỉ giữa các buổi (phút)<input type="number" min={0} max={60} value={settings.breakMinutes} onChange={e => change({ ...settings, breakMinutes: Number(e.target.value) })} /></label></div>
        <details open={selection.missingBusyCalendarIds.length > 0 || failedCalendarIds.length > 0 ? true : undefined}><summary>Lịch cần kiểm tra giờ bận ({settings.busyCalendarIds.length})</summary><p>Lịch nhận buổi học luôn được kiểm tra. Chọn thêm tất cả lịch có việc cá nhân để tránh trùng giờ.</p>
          {failedCalendarIds.length > 0 && <div className="calendar-export-error" role="alert">
            <p>Chưa đọc được giờ bận: <strong>{failedCalendarIds.map(id => calendars.find(item => item.id === id)?.name || id).join(', ')}</strong>.</p>
            <p>Nếu là lịch ngày lễ hoặc lịch bạn không cần tránh, bạn có thể bỏ chọn rồi xem trước lại. Nếu có công việc cá nhân, hãy khôi phục quyền truy cập hoặc kết nối đúng tài khoản. App không tự bỏ qua lịch này.</p>
            {failedCalendarIds.includes(settings.targetCalendarId) && <p>Lịch nhận cũng đang lỗi: bỏ dấu chọn không đủ vì lịch nhận luôn được kiểm tra. Hãy đổi lịch nhận có quyền ghi hoặc kết nối lại và cấp đủ quyền.</p>}
          </div>}
          {selection.missingBusyCalendarIds.length > 0 && <div className="calendar-export-error" role="alert">
            <p>{selection.missingBusyCalendarIds.length} lịch đã chọn không còn trong danh sách Google trả về. App chưa đọc được giờ bận của các lịch này. Chỉ bỏ chọn nếu bạn không cần tránh lịch đó; nếu vẫn cần, hãy khôi phục quyền truy cập rồi tải lại kết nối.</p>
            {selection.missingBusyCalendarIds.map(id => <label className="calendar-export-check calendar-export-missing" key={id}><input type="checkbox" checked onChange={() => change({ ...settings, busyCalendarIds: settings.busyCalendarIds.filter(value => value !== id) })} /><span>Không còn truy cập được: <code>{id}</code></span></label>)}
          </div>}
          {calendars.map(item => <label className={`calendar-export-check ${failedCalendarIds.includes(item.id) ? 'calendar-export-missing' : ''}`} key={item.id}><input type="checkbox" checked={settings.busyCalendarIds.includes(item.id)} onChange={e => change({ ...settings, busyCalendarIds: e.target.checked ? [...settings.busyCalendarIds, item.id] : settings.busyCalendarIds.filter(id => id !== item.id) })} />{item.name}{failedCalendarIds.includes(item.id) && ' — Không đọc được giờ bận'}</label>)}</details>
        <div className="calendar-export-hours">{settings.days.map((day, index) => <div key={day.weekday}>
          <label className="calendar-export-check"><input type="checkbox" checked={day.enabled} onChange={e => change({ ...settings, days: settings.days.map((value, i) => i === index ? { ...value, enabled: e.target.checked } : value) })} />{weekdays[day.weekday - 1]}</label>
          <input aria-label={`Bắt đầu học ${weekdays[day.weekday - 1]}`} type="time" disabled={!day.enabled} value={day.start} onChange={e => change({ ...settings, days: settings.days.map((value, i) => i === index ? { ...value, start: e.target.value } : value) })} />
          <span>đến</span><input aria-label={`Kết thúc học ${weekdays[day.weekday - 1]}`} type="time" disabled={!day.enabled} value={day.end} onChange={e => change({ ...settings, days: settings.days.map((value, i) => i === index ? { ...value, end: e.target.value } : value) })} />
        </div>)}</div>
        <h3>Ăn uống, ngủ và nghỉ ngơi</h3><p>Các khoảng này được loại khỏi giờ học. Giờ kết thúc sớm hơn giờ bắt đầu được hiểu là qua đêm.</p>
        {settings.blocks.map((block, index) => <div className="calendar-export-block" key={index}>
          <div><input aria-label={`Tên khoảng nghỉ ${index + 1}`} maxLength={100} value={block.label} onChange={e => change({ ...settings, blocks: settings.blocks.map((value, i) => i === index ? { ...value, label: e.target.value } : value) })} />
            <input aria-label={`Bắt đầu ${block.label}`} type="time" value={block.start} onChange={e => change({ ...settings, blocks: settings.blocks.map((value, i) => i === index ? { ...value, start: e.target.value } : value) })} /><span>đến</span>
            <input aria-label={`Kết thúc ${block.label}`} type="time" value={block.end} onChange={e => change({ ...settings, blocks: settings.blocks.map((value, i) => i === index ? { ...value, end: e.target.value } : value) })} />
            <button type="button" aria-label={`Bỏ ${block.label}`} onClick={() => change({ ...settings, blocks: settings.blocks.filter((_, i) => i !== index) })}>Bỏ</button></div>
          <div className="calendar-export-weekdays">{weekdays.map((name, i) => <label key={name}><input type="checkbox" checked={block.weekdays.includes(i + 1)} onChange={e => change({ ...settings, blocks: settings.blocks.map((value, j) => j === index ? { ...value, weekdays: e.target.checked ? [...value.weekdays, i + 1] : value.weekdays.filter(day => day !== i + 1) } : value) })} />{name}</label>)}</div>
        </div>)}
        <button type="button" disabled={settings.blocks.length >= 30} onClick={() => change({ ...settings, blocks: [...settings.blocks, { label: 'Nghỉ ngơi', start: '15:00', end: '15:30', weekdays: [1, 2, 3, 4, 5, 6, 7] }] })}>+ Thêm khoảng nghỉ</button>
      </fieldset>
      {invalidSelection && <p className="calendar-export-problem" role="status">Kiểm tra lại lịch nhận và các lịch không còn truy cập được ở trên trước khi xem trước giờ học.</p>}
      <div className="calendar-export-actions"><button className="study-primary" type="button" disabled={busy || invalidSelection} onClick={() => run(async () => {
        await saveCalendarSettings(settings)
        const value = await previewCalendar(from, goalId)
        if (live.current) { setPreview(value); setResult(null) }
      })}>{busy ? 'Đang xử lý…' : 'Lưu giờ sinh hoạt & xem trước'}</button></div>
      {preview && <section className="calendar-export-preview" aria-label="Xem trước lịch Google"><h3>Giờ học đề xuất</h3>
        <p>{preview.alreadyExported} buổi đã xuất trước đó. Bản xem trước có hiệu lực 15 phút; chỉ xác nhận mới ghi lên Google.</p>
        {!preview.slots.length && <p>Không có buổi học mới để xuất trong tuần này. Các buổi đã xong hoặc đã xuất được bỏ qua.</p>}
        <ul>{preview.slots.map(slot => <li key={slot.task.id}><div><strong>{slot.task.title}</strong><span>{slot.task.subject} · {slot.task.date} · {slot.task.minutes} phút</span></div>
          {slot.start ? <span>{formatTime(slot.start, preview.settings.timezone)}–{formatTime(slot.end, preview.settings.timezone)}</span> : <span className="calendar-export-problem">{slot.reason}</span>}</li>)}</ul>
        {!canExport && preview.slots.length > 0 && <p role="status">Chưa đủ giờ trống. Hãy chỉnh giờ học hoặc lịch công việc trước khi xuất. App không tự tách buổi học.</p>}
        <button className="study-primary" type="button" disabled={busy || !canExport} onClick={() => run(async () => {
          const value = await confirmCalendar(preview.id)
          if (live.current) setResult(value)
        })}>Xác nhận xuất {preview.slots.filter(slot => slot.start).length} buổi học</button>
      </section>}
      {result && <div className="calendar-export-results" role="status"><h3>Đã xác nhận {result.confirmed} buổi · {result.failed} buổi chưa xuất được</h3>
        <ul>{result.results.map(item => <li key={item.taskId}><strong>{item.title}</strong><p>{item.message}</p>{eventLink(item.url) && <a href={eventLink(item.url)} target="_blank" rel="noreferrer">Mở trên Google Calendar ↗</a>}</li>)}</ul>
        {result.failed > 0 && <p>Đọc lý do của từng buổi. Nếu lỗi mạng, có thể xác nhận lại cùng bản xem trước; app sẽ kiểm tra sự kiện đã tạo để tránh bản sao. Nếu trùng giờ, hãy xem trước lại.</p>}
      </div>}
      <p className="calendar-export-note">Đồng bộ một chiều từ app. Các sửa/xóa mới được gửi qua hàng chờ; xem trạng thái đồng bộ để biết Google đã cập nhật chưa. Thay đổi đồng thời từ ứng dụng khác vẫn có thể gây trùng sau lần kiểm tra giờ bận.</p>
    </>}
  </section>
}
