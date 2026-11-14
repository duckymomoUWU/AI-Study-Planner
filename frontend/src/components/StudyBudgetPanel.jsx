import { useEffect, useRef, useState } from 'react'
import { getStudyBudget, saveStudyBudget } from '../lib/studyBudget.js'
import './StudyBudgetPanel.css'

export default function StudyBudgetPanel({ from, to, revision, onSaved, focusRequest = 0 }) {
  const panelRef = useRef(null)
  const lastFocusRequest = useRef(0)
  const [settings, setSettings] = useState(null)
  const [minutes, setMinutes] = useState('')
  const [overrides, setOverrides] = useState({})
  const [date, setDate] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    let active = true
    getStudyBudget(from, to).then(value => {
      if (!active) return
      setSettings(value); setMinutes(value.defaultMinutes ?? ''); setOverrides(value.dailyMinutes); setError('')
    }).catch(err => { if (active) setError(err.message) })
    return () => { active = false }
  }, [from, to, revision, retry])
  useEffect(() => {
    if (!focusRequest || !settings || lastFocusRequest.current === focusRequest) return
    lastFocusRequest.current = focusRequest
    const details = panelRef.current?.querySelector('details')
    if (details) details.open = true
    panelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    panelRef.current?.querySelector('input')?.focus({ preventScroll: true })
  }, [focusRequest, settings])

  async function save(event) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try {
      await saveStudyBudget(settings, minutes, overrides)
      setNotice('Đã lưu quỹ phút chung. Lịch cũ được giữ nguyên; hãy xếp lại bản nháp nếu cần.')
      const value = await getStudyBudget(from, to)
      setSettings(value); setMinutes(value.defaultMinutes); setOverrides(value.dailyMinutes)
      onSaved?.()
      window.dispatchEvent(new CustomEvent('study-budget-saved'))
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <section ref={panelRef} className="study-budget" aria-label="Quỹ thời gian chung">
    <h2>Quỹ thời gian chung</h2>
    <p>Giới hạn tổng phút của tất cả môn trong một ngày. Phút/ngày từng môn chỉ là mức mong muốn cho AI; công việc đã hoàn thành vẫn tính vào quỹ chung.</p>
    {error && <p role="alert">{error} <button type="button" disabled={busy} onClick={() => setRetry(value => value + 1)}>Tải lại quỹ phút</button></p>}
    {notice && <p role="status">{notice}</p>}
    {!settings && !error && <p role="status">Đang tải quỹ phút…</p>}
    {settings && <>
      {settings.defaultMinutes === null && <p role="status"><strong>Bạn chưa đặt quỹ chung.</strong> Lưu số phút thực sự rảnh trước khi thêm việc hoặc tạo lịch AI. Lịch hiện có vẫn được giữ.</p>}
      <details open={settings.defaultMinutes === null}>
        <summary>{settings.defaultMinutes === null ? 'Đặt quỹ phút mỗi ngày' : `Chỉnh quỹ chung · mặc định ${settings.defaultMinutes} phút/ngày`}</summary>
        <form onSubmit={save}>
          <fieldset disabled={busy}>
            <label>Phút/ngày cho tất cả môn<input type="number" required min="0" max="1440" step="1" value={minutes} onChange={e => setMinutes(e.target.value)} /></label>
            <p>Ngày không chỉnh riêng dùng mức mặc định. Đặt 0 phút để nghỉ; thay đổi quỹ không tự dời lịch đã lưu.</p>
            <div className="budget-overrides">{Object.entries(overrides).sort(([a], [b]) => a.localeCompare(b)).map(([day, value]) => <div key={day}>
              <label>{day}<input aria-label={`Quỹ chung ngày ${day}`} type="number" required min="0" max="1440" step="1" value={value} onChange={e => setOverrides(previous => ({ ...previous, [day]: e.target.value }))} /></label>
              <button type="button" onClick={() => setOverrides(previous => Object.fromEntries(Object.entries(previous).filter(([key]) => key !== day)))}>Dùng mức mặc định</button>
            </div>)}</div>
            <label>Ngày cần chỉnh riêng<input type="date" value={date} onChange={e => setDate(e.target.value)} /></label>
            <button type="button" disabled={!date || Object.keys(overrides).length >= 366} onClick={() => { setOverrides(previous => ({ ...previous, [date]: previous[date] ?? minutes })); setDate('') }}>Thêm ngày</button>
            <button type="submit">{busy ? 'Đang lưu…' : 'Lưu quỹ thời gian chung'}</button>
          </fieldset>
        </form>
      </details>
      <ul className="budget-days">{settings.days.map(day => <li key={day.date}>
        <strong>{day.date}</strong><span>{day.usedMinutes} / {day.budgetMinutes ?? '—'} phút</span>
        <small>{day.remainingMinutes === null ? 'Chưa đặt quỹ' : `Còn ${day.remainingMinutes} phút`}</small>
      </li>)}</ul>
      <small>Số phút trên tính tất cả mục tiêu, kể cả khi bạn đang lọc một môn.</small>
    </>}
  </section>
}
